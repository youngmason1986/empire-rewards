package com.blackempire.rewards;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import com.unity3d.services.banners.BannerView;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Empire Rewards — walk-to-earn + receipt rewards + rewarded ads, v3.
 *
 * Single activity, four screens (Home / Walk / Receipts / Rewards).
 * All state is local (PointsStore / SharedPreferences). No server, no
 * payout promises: redemption requests are stored on the phone and
 * gift-card fulfilment waits for a rewards partner.
 *
 * v5 adds George's gift-card rule (1,000 pts = $1; redeeming opens at
 * 1,000 points; ad points and walking points count together in the one
 * balance): $15/$50/$100 rows, a brand picker (Amazon, Google Play,
 * Apple, Steam, PlayStation, Xbox), and sign-in required to redeem.
 * Requests are still saved on the phone and paid from ad revenue —
 * never an instant payout.
 */
public class MainActivity extends Activity
        implements SensorEventListener, TestAdProvider.TestAdScreen {

    private static final int RC_ACTIVITY_PERM = 101;
    private static final int RC_CAMERA_PERM = 102;
    private static final int RC_TAKE_PHOTO = 201;
    private static final int RC_SIGN_IN = 301;

    private PointsStore store;
    private AdProvider adProvider;

    // Sign-in (Firebase Auth, v3)
    private FirebaseAuth auth;
    private GoogleSignInClient googleClient;
    private boolean authUnavailable = false;
    private boolean emailSignUpMode = true;

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private boolean sensorAvailable = false;

    private CountDownTimer adTimer;
    private Runnable pendingAdReward;

    // Header
    private TextView headerPoints;
    // Account bar
    private TextView accountLabel;
    // Sign-in screen
    private View screenAuth;
    private TextView authError;
    private EditText emailField, passwordField;
    private Button btnEmailAuth, btnAuthMode;
    // Home
    private TextView stepsHome, stepsToBoxHome, pointsHome, boxesHome;
    private ProgressBar progressHome;
    private ListView historyList;
    // Walk
    private TextView stepsWalk, stepsToBoxWalk, sensorStatus;
    private ProgressBar progressWalk;
    private Button[] boxButtons = new Button[3];
    private LinearLayout bonusBoxRow;
    // Receipts
    private TextView receiptsCount;
    private ImageView receiptThumb;
    private ListView receiptHistoryList;
    // Rewards
    private TextView pointsRewards;
    private ListView redemptionList;
    // Screens + nav
    private View screenHome, screenWalk, screenReceipts, screenRewards;
    private Button navHome, navWalk, navReceipts, navRewards;
    // Test-ad overlay
    private View adOverlay;
    private TextView adCountdown;
    private Button adClose;
    // Live banner (Unity, Home screen only)
    private FrameLayout bannerContainer;
    private BannerView bannerView;
    private boolean bannerLoaded = false;
    private int currentScreen = 0;
    // Interstitial rate-limit: at most once every 2 minutes, and never
    // on two box opens in a row.
    private long lastInterstitialShownAt = 0;
    private boolean lastBoxOpenShowedInterstitial = false;
    private boolean interstitialPending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initAuth();
        store = new PointsStore(this);
        AdManager.initialize(this);
        adProvider = AdManager.provider(this);
        setupBanner();

        bindViews();
        wireNav();
        wireHomeButtons();
        wireWalkBoxes();
        wireReceipts();
        wireRewards();
        wireAuth();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            sensorAvailable = stepSensor != null;
        }
        if (!sensorAvailable) {
            sensorStatus.setText("Step counter not available on this device — walking rewards need a phone with a step-counter sensor.");
        }

        maybeRequestActivityPermission();
        showScreen(0);
        refreshAll();
        // Sign-in gate (v3): a signed-in user or a returning guest goes
        // straight in; everyone else sees the sign-in screen first.
        if (auth != null && auth.getCurrentUser() != null) {
            store.setOwner(auth.getCurrentUser().getUid());
            enterApp();
        } else if (isGuestMode()) {
            store.setOwner(null);
            enterApp();
        } else {
            showAuthScreen();
        }
    }

    /** Set up Firebase Auth + the Google sign-in client. If Firebase
     * failed to initialize (RewardsApp), auth stays null and the
     * sign-in screen says so plainly instead of crashing. */
    private void initAuth() {
        try {
            auth = FirebaseAuth.getInstance();
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                    GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(FirebaseConfig.WEB_CLIENT_ID)
                    .requestEmail()
                    .build();
            googleClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception e) {
            auth = null;
            authUnavailable = true;
        }
    }

    private void bindViews() {
        headerPoints = findViewById(R.id.headerPoints);
        accountLabel = findViewById(R.id.accountLabel);
        // Sign-in screen
        screenAuth = findViewById(R.id.screenAuth);
        authError = findViewById(R.id.authError);
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        btnEmailAuth = findViewById(R.id.btnEmailAuth);
        btnAuthMode = findViewById(R.id.btnAuthMode);
        // Home
        stepsHome = findViewById(R.id.stepsHome);
        stepsToBoxHome = findViewById(R.id.stepsToBoxHome);
        pointsHome = findViewById(R.id.pointsHome);
        boxesHome = findViewById(R.id.boxesHome);
        progressHome = findViewById(R.id.progressHome);
        historyList = findViewById(R.id.historyList);
        // Walk
        stepsWalk = findViewById(R.id.stepsWalk);
        stepsToBoxWalk = findViewById(R.id.stepsToBoxWalk);
        sensorStatus = findViewById(R.id.sensorStatus);
        progressWalk = findViewById(R.id.progressWalk);
        boxButtons[0] = findViewById(R.id.box0);
        boxButtons[1] = findViewById(R.id.box1);
        boxButtons[2] = findViewById(R.id.box2);
        bonusBoxRow = findViewById(R.id.bonusBoxRow);
        // Receipts
        receiptsCount = findViewById(R.id.receiptsCount);
        receiptThumb = findViewById(R.id.receiptThumb);
        receiptHistoryList = findViewById(R.id.receiptHistoryList);
        // Rewards
        pointsRewards = findViewById(R.id.pointsRewards);
        redemptionList = findViewById(R.id.redemptionList);
        // Screens + nav
        screenHome = findViewById(R.id.screenHome);
        screenWalk = findViewById(R.id.screenWalk);
        screenReceipts = findViewById(R.id.screenReceipts);
        screenRewards = findViewById(R.id.screenRewards);
        navHome = findViewById(R.id.navHome);
        navWalk = findViewById(R.id.navWalk);
        navReceipts = findViewById(R.id.navReceipts);
        navRewards = findViewById(R.id.navRewards);
        // Test-ad overlay
        adOverlay = findViewById(R.id.adOverlay);
        adCountdown = findViewById(R.id.adCountdown);
        adClose = findViewById(R.id.adClose);
        // Live banner container
        bannerContainer = findViewById(R.id.bannerContainer);
    }

    // ---- Navigation ----

    private void wireNav() {
        navHome.setOnClickListener(v -> showScreen(0));
        navWalk.setOnClickListener(v -> showScreen(1));
        navReceipts.setOnClickListener(v -> showScreen(2));
        navRewards.setOnClickListener(v -> showScreen(3));
    }

    private void showScreen(int index) {
        currentScreen = index;
        screenHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        screenWalk.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        screenReceipts.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        screenRewards.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        navHome.setTextColor(index == 0 ? 0xFFFFD700 : 0xFFFFFFFF);
        navWalk.setTextColor(index == 1 ? 0xFFFFD700 : 0xFFFFFFFF);
        navReceipts.setTextColor(index == 2 ? 0xFFFFD700 : 0xFFFFFFFF);
        navRewards.setTextColor(index == 3 ? 0xFFFFD700 : 0xFFFFFFFF);
        // Banner lives on the Home screen only, and only once one has
        // actually loaded — it never covers buttons on any screen.
        bannerContainer.setVisibility(
                index == 0 && bannerLoaded ? View.VISIBLE : View.GONE);
        refreshAll();
    }

    // ---- Home buttons ----

    private void wireHomeButtons() {
        findViewById(R.id.btnLucky).setOnClickListener(v -> {
            int points = store.openLuckyBox();
            if (points < 0) {
                toast("Lucky Box already opened today — come back tomorrow!");
            } else {
                toast("Lucky Box! +" + points + " pts");
            }
            refreshAll();
        });

        findViewById(R.id.btnAdPoints).setOnClickListener(v -> showRewardedAd(false));
        findViewById(R.id.btnAdBox).setOnClickListener(v -> showRewardedAd(true));
    }

    private void showRewardedAd(final boolean forBox) {
        adProvider.showRewardedAd(this, new AdProvider.RewardCallback() {
            @Override
            public void onRewardEarned() {
                runOnUiThread(() -> {
                    if (forBox) {
                        if (store.addBonusBox()) {
                            toast("Bonus box earned! Open it on the Walk screen.");
                        } else {
                            toast("Bonus box limit reached for today (max 3).");
                        }
                    } else {
                        store.addPoints(PointsStore.POINTS_PER_AD, "Rewarded ad");
                        toast("+" + PointsStore.POINTS_PER_AD + " pts for watching the ad!");
                    }
                    refreshAll();
                });
            }

            @Override
            public void onAdUnavailable(final String reason) {
                runOnUiThread(() -> toast("Ad not available: " + reason));
            }
        });
    }

    // ---- Walk screen ----

    private void wireWalkBoxes() {
        for (int i = 0; i < boxButtons.length; i++) {
            final int index = i;
            boxButtons[i].setOnClickListener(v -> {
                int points = store.openWalkBox(index);
                if (points > 0) {
                    toast("Loot box opened! +" + points + " pts");
                    maybeShowInterstitial();
                } else {
                    toast("That box is locked or already opened.");
                }
                refreshAll();
            });
        }
    }

    // ---- Receipts ----

    private void wireReceipts() {
        findViewById(R.id.btnSnap).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && checkSelfPermission(Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, RC_CAMERA_PERM);
                return;
            }
            launchCamera();
        });
        restoreLastReceiptThumb();
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, RC_TAKE_PHOTO);
        } else {
            toast("No camera app found on this phone.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            handleGoogleSignInResult(data);
            return;
        }
        if (requestCode == RC_TAKE_PHOTO && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            Bitmap thumb = extras != null ? (Bitmap) extras.get("data") : null;
            if (thumb != null) {
                saveReceiptThumb(thumb);
                receiptThumb.setImageBitmap(thumb);
                receiptThumb.setVisibility(View.VISIBLE);
            }
            if (store.addReceipt()) {
                toast("Receipt accepted! +" + PointsStore.POINTS_PER_RECEIPT
                        + " pts (Pending review)");
            } else {
                toast("Receipt limit reached for today (max 3). Photo saved, no points.");
            }
            refreshAll();
        }
    }

    private void saveReceiptThumb(Bitmap bitmap) {
        String name = "receipt_" + System.currentTimeMillis() + ".jpg";
        try (FileOutputStream out = openFileOutput(name, MODE_PRIVATE)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
            getSharedPreferences("empire_rewards_ui", MODE_PRIVATE)
                    .edit().putString("last_receipt_thumb", name).apply();
        } catch (Exception e) {
            // Thumbnail display is a bonus; the receipt itself still counts.
        }
    }

    private void restoreLastReceiptThumb() {
        SharedPreferences ui = getSharedPreferences("empire_rewards_ui", MODE_PRIVATE);
        String name = ui.getString("last_receipt_thumb", null);
        if (name == null) return;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(getFileStreamPath(name).getAbsolutePath());
            if (bitmap != null) {
                receiptThumb.setImageBitmap(bitmap);
                receiptThumb.setVisibility(View.VISIBLE);
            }
        } catch (Exception ignored) {
        }
    }

    // ---- Rewards screen ----

    private void wireRewards() {
        findViewById(R.id.redeem1).setOnClickListener(v -> redeem(1));
        findViewById(R.id.redeem5).setOnClickListener(v -> redeem(5));
        findViewById(R.id.redeem10).setOnClickListener(v -> redeem(10));
        findViewById(R.id.redeem20).setOnClickListener(v -> redeem(20));
        findViewById(R.id.redeem15).setOnClickListener(v -> redeem(15));
        findViewById(R.id.redeem50).setOnClickListener(v -> redeem(50));
        findViewById(R.id.redeem100).setOnClickListener(v -> redeem(100));
    }

    /** Gift-card brands the user can pick from when redeeming. */
    private static final String[] GIFT_CARD_BRANDS = {
            "Amazon", "Google Play", "Apple", "Steam", "PlayStation", "Xbox"
    };

    private void redeem(int dollars) {
        // Guests cannot redeem: a request needs a signed-in account so
        // the gift card has somewhere to go. Route to sign-in plainly.
        if (auth == null) {
            toast("Sign-in is unavailable right now — gift cards need an "
                    + "account. Check your connection and restart the app.");
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Sign in first to redeem a gift card — your request needs "
                    + "an account to send it to.");
            showAuthScreen();
            return;
        }
        int cost = dollars * PointsStore.POINTS_PER_DOLLAR;
        if (store.getBalance() < cost) {
            toast("Not enough points yet — a $" + dollars + " gift card needs "
                    + cost + " pts. Keep earning!");
            return;
        }
        // Step 1: pick the gift-card brand.
        new AlertDialog.Builder(this)
                .setTitle("Pick your $" + dollars + " gift card")
                .setItems(GIFT_CARD_BRANDS, (dialog, which) -> {
                    confirmRedemption(dollars, cost, GIFT_CARD_BRANDS[which], user);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Step 2: confirm brand + amount + account, then save the request. */
    private void confirmRedemption(int dollars, int cost, String brand,
                                   FirebaseUser user) {
        String email = user.getEmail();
        if (email == null || email.isEmpty()) email = user.getDisplayName();
        if (email == null || email.isEmpty()) email = "your account";
        final String delivery = email;
        new AlertDialog.Builder(this)
                .setTitle("$" + dollars + " " + brand + " gift card — " + cost + " pts")
                .setMessage("Your request will be saved on this phone and "
                        + "sent for review, for " + delivery + ". Requests are "
                        + "reviewed and paid from ad revenue — this is not an "
                        + "instant payout and no code is sent straight away. "
                        + "Save this request?")
                .setPositiveButton("Save request", (dialog, which) -> {
                    store.spendPoints(cost, "$" + dollars + " " + brand
                            + " gift card redemption request");
                    store.addRedemption("$" + dollars + " " + brand + " gift card · "
                            + cost + " pts · for " + delivery
                            + " · saved, waiting for review (paid from ad revenue)");
                    toast("Request saved! It will be reviewed and paid from "
                            + "ad revenue — no instant code is sent.");
                    refreshAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Steps / sensor ----

    private void maybeRequestActivityPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, RC_ACTIVITY_PERM);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorAvailable && stepSensor != null
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                                == PackageManager.PERMISSION_GRANTED)) {
            sensorManager.registerListener(this, stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            sensorStatus.setText("Step counter running — walk to unlock Loot Boxes.");
        }
        refreshAll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (adTimer != null) adTimer.cancel();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            store.onStepCounter((long) event.values[0]);
            refreshStepDisplays();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed.
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            if (requestCode == RC_ACTIVITY_PERM) {
                sensorStatus.setText("Step permission denied — walking rewards are off. "
                        + "Allow Physical activity in Settings to turn them on.");
            } else if (requestCode == RC_CAMERA_PERM) {
                toast("Camera permission needed to snap receipts.");
            }
            return;
        }
        if (requestCode == RC_ACTIVITY_PERM) {
            onResume();
        } else if (requestCode == RC_CAMERA_PERM) {
            launchCamera();
        }
    }

    // ---- Sign-in (Firebase Auth, v3) ----

    private void wireAuth() {
        findViewById(R.id.btnGoogle).setOnClickListener(v -> startGoogleSignIn());
        btnEmailAuth.setOnClickListener(v -> submitEmailAuth());
        btnAuthMode.setOnClickListener(v -> {
            emailSignUpMode = !emailSignUpMode;
            updateEmailAuthUi();
        });
        findViewById(R.id.btnGuest).setOnClickListener(v -> {
            setGuestMode(true);
            store.setOwner(null);
            enterApp();
            toast("Continuing as a guest — your points stay on this phone only.");
        });
        findViewById(R.id.btnSignOut).setOnClickListener(v -> signOut());
        updateEmailAuthUi();
        if (authUnavailable) {
            showAuthError("Sign-in is unavailable right now — Firebase did not "
                    + "start. Check your connection and restart the app.");
        }
    }

    private void updateEmailAuthUi() {
        btnEmailAuth.setText(emailSignUpMode ? "Create account" : "Sign in");
        btnAuthMode.setText(emailSignUpMode
                ? "Already have an account? Sign in"
                : "New here? Create an account");
    }

    /** Show the sign-in screen and hide the whole app behind it. */
    private void showAuthScreen() {
        setMainUiVisible(false);
        screenAuth.setVisibility(View.VISIBLE);
    }

    /** Hide the sign-in screen and show the app. */
    private void enterApp() {
        screenAuth.setVisibility(View.GONE);
        setMainUiVisible(true);
        showScreen(0);
        refreshAll();
    }

    private void setMainUiVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        findViewById(R.id.header).setVisibility(v);
        findViewById(R.id.accountBar).setVisibility(v);
        findViewById(R.id.screenContainer).setVisibility(v);
        findViewById(R.id.bottomNav).setVisibility(v);
        if (!visible) bannerContainer.setVisibility(View.GONE);
    }

    private boolean isGuestMode() {
        return getSharedPreferences("empire_rewards_ui", MODE_PRIVATE)
                .getBoolean("guest_mode", false);
    }

    private void setGuestMode(boolean guest) {
        getSharedPreferences("empire_rewards_ui", MODE_PRIVATE)
                .edit().putBoolean("guest_mode", guest).apply();
    }

    private void refreshAccount() {
        if (auth != null && auth.getCurrentUser() != null) {
            FirebaseUser fu = auth.getCurrentUser();
            String who = fu.getDisplayName();
            if (who == null || who.isEmpty()) who = fu.getEmail();
            if (who == null || who.isEmpty()) who = "Signed in";
            accountLabel.setText("Signed in as " + who);
        } else {
            accountLabel.setText("Guest — points stay on this phone");
        }
    }

    private void signOut() {
        try {
            if (auth != null) auth.signOut();
            if (googleClient != null) googleClient.signOut();
        } catch (Exception ignored) {
        }
        setGuestMode(false);
        store.setOwner(null);
        showAuthScreen();
    }

    private void showAuthError(String message) {
        authError.setText(message);
        authError.setVisibility(View.VISIBLE);
    }

    private void clearAuthError() {
        authError.setVisibility(View.GONE);
    }

    private void startGoogleSignIn() {
        if (auth == null || googleClient == null) {
            showAuthError("Sign-in is unavailable right now — Firebase did not "
                    + "start. Check your connection and restart the app.");
            return;
        }
        clearAuthError();
        try {
            startActivityForResult(googleClient.getSignInIntent(), RC_SIGN_IN);
        } catch (Exception e) {
            showAuthError("Could not open Google sign-in. Try again.");
        }
    }

    private void handleGoogleSignInResult(Intent data) {
        try {
            Task<GoogleSignInAccount> task =
                    GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount acct = task.getResult(ApiException.class);
            if (acct == null || acct.getIdToken() == null) {
                showAuthError("Google sign-in returned no token. Try again.");
                return;
            }
            AuthCredential cred =
                    GoogleAuthProvider.getCredential(acct.getIdToken(), null);
            auth.signInWithCredential(cred)
                    .addOnSuccessListener(r -> {
                        FirebaseUser fu = r.getUser();
                        if (fu == null) {
                            showAuthError("Google sign-in failed — no account came back.");
                        } else {
                            onSignedIn(fu);
                        }
                    })
                    .addOnFailureListener(e -> showAuthError(
                            "Google sign-in failed: " + friendlyAuthError(e)));
        } catch (ApiException e) {
            showAuthError("Google sign-in failed (" + e.getStatusCode() + "): "
                    + "this usually means this app is not registered in the "
                    + "Firebase project yet. No account was created.");
        }
    }

    private void submitEmailAuth() {
        if (auth == null) {
            showAuthError("Sign-in is unavailable right now — Firebase did not "
                    + "start. Check your connection and restart the app.");
            return;
        }
        clearAuthError();
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();
        if (email.isEmpty() || !email.contains("@")) {
            showAuthError("Enter a valid email address.");
            return;
        }
        if (password.length() < 6) {
            showAuthError("Password must be at least 6 characters.");
            return;
        }
        if (emailSignUpMode) {
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(r -> {
                        FirebaseUser fu = r.getUser();
                        if (fu == null) {
                            showAuthError("Account was not created. Try again.");
                            return;
                        }
                        // Verification email is sent but never blocks entry in v1.
                        try {
                            fu.sendEmailVerification();
                        } catch (Exception ignored) {
                        }
                        onSignedIn(fu);
                    })
                    .addOnFailureListener(e -> showAuthError(friendlyAuthError(e)));
        } else {
            auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(r -> {
                        FirebaseUser fu = r.getUser();
                        if (fu == null) {
                            showAuthError("Sign-in failed — no account came back.");
                        } else {
                            onSignedIn(fu);
                        }
                    })
                    .addOnFailureListener(e -> showAuthError(friendlyAuthError(e)));
        }
    }

    private void onSignedIn(FirebaseUser fu) {
        setGuestMode(false);
        store.setOwner(fu.getUid());
        enterApp();
        String who = fu.getDisplayName();
        if (who == null || who.isEmpty()) who = fu.getEmail();
        toast("Signed in" + (who != null ? " as " + who : "") + "!");
    }

    /** Plain-English auth errors: never a stack trace, never a crash. */
    private String friendlyAuthError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("ERROR_EMAIL_ALREADY_IN_USE")
                || msg.contains("email address is already in use")) {
            return "That email already has an account — tap \"Already have an "
                    + "account? Sign in\" and try signing in instead.";
        }
        if (msg.contains("ERROR_WRONG_PASSWORD")
                || msg.contains("ERROR_INVALID_CREDENTIAL")
                || msg.contains("password is invalid")
                || msg.contains("credential is malformed")) {
            return "Wrong email or password. Check them and try again.";
        }
        if (msg.contains("ERROR_USER_NOT_FOUND")
                || msg.contains("no user record")) {
            return "No account with that email — tap \"New here? Create an "
                    + "account\" to sign up.";
        }
        if (msg.contains("ERROR_WEAK_PASSWORD")
                || msg.contains("password is invalid")) {
            return "Password must be at least 6 characters.";
        }
        if (msg.contains("ERROR_INVALID_EMAIL")
                || msg.contains("email address is badly formatted")) {
            return "That email address doesn't look right.";
        }
        if (msg.contains("ERROR_NETWORK_REQUEST_FAILED")
                || msg.contains("network error")) {
            return "No network connection — check your data and try again.";
        }
        if (msg.contains("DEVELOPER_ERROR") || msg.contains("12500")) {
            return "Google sign-in is not set up for this app yet — the app "
                    + "still needs registering in the Firebase project.";
        }
        if (msg.contains("ERROR_TOO_MANY_REQUESTS")) {
            return "Too many attempts — wait a little and try again.";
        }
        return "Sign-in failed: " + msg;
    }

    // ---- Live ads: banner + interstitial ----

    /** Load the Unity banner once ads have initialized. The banner sits
     * in its reserved 50dp strip at the bottom of the Home screen only,
     * and the strip stays hidden until a banner actually loads — a
     * failed banner is silent and never blocks the rest of the app. */
    private void setupBanner() {
        if (!AdManager.isUnityConfigured()) return;
        UnityAdsProvider.runWhenInitialized(() -> runOnUiThread(() -> {
            if (isFinishing() || bannerView != null) return;
            try {
                bannerView = UnityAdsProvider.createBanner(this,
                        () -> {
                            bannerLoaded = true;
                            if (currentScreen == 0) {
                                bannerContainer.setVisibility(View.VISIBLE);
                            }
                        },
                        () -> {
                            bannerLoaded = false;
                            bannerContainer.setVisibility(View.GONE);
                        });
                if (bannerView != null) {
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.CENTER_HORIZONTAL;
                    bannerContainer.addView(bannerView, lp);
                }
            } catch (Throwable t) {
                // Banner is optional; never crash for it.
                bannerLoaded = false;
            }
        }));
    }

    /**
     * Show an interstitial after a Loot Box is opened, rate-limited:
     * at most once every 2 minutes, never on two box opens in a row,
     * and never while another interstitial load is in flight. A failed
     * load/show is silent. Interstitials never grant points.
     */
    private void maybeShowInterstitial() {
        if (!AdManager.isUnityConfigured() || interstitialPending) return;
        if (lastBoxOpenShowedInterstitial) {
            lastBoxOpenShowedInterstitial = false;
            return;
        }
        if (System.currentTimeMillis() - lastInterstitialShownAt < 120_000) return;
        interstitialPending = true;
        try {
            new UnityAdsProvider().showInterstitial(this,
                    () -> {
                        lastInterstitialShownAt = System.currentTimeMillis();
                        lastBoxOpenShowedInterstitial = true;
                        interstitialPending = false;
                    },
                    () -> interstitialPending = false);
        } catch (Throwable t) {
            interstitialPending = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bannerView != null) {
            try {
                bannerView.destroy();
            } catch (Throwable ignored) {
            }
            bannerView = null;
        }
    }

    // ---- Test-ad overlay ----

    @Override
    public void showTestAdScreen(final Runnable onFinished) {
        pendingAdReward = onFinished;
        adClose.setVisibility(View.GONE);
        adCountdown.setText("Reward in 5…");
        adOverlay.setVisibility(View.VISIBLE);
        if (adTimer != null) adTimer.cancel();
        adTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                adCountdown.setText("Reward in " + seconds + "…");
            }

            @Override
            public void onFinish() {
                adCountdown.setText("Reward earned!");
                adClose.setVisibility(View.VISIBLE);
            }
        };
        adTimer.start();
        adClose.setOnClickListener(v -> {
            adOverlay.setVisibility(View.GONE);
            if (pendingAdReward != null) {
                Runnable r = pendingAdReward;
                pendingAdReward = null;
                r.run();
            }
        });
    }

    // ---- UI refresh ----

    /** Light refresh for step-counter events (no list rebuilds). */
    private void refreshStepDisplays() {
        int steps = store.getStepsToday();
        stepsHome.setText(String.valueOf(steps));
        stepsWalk.setText(steps + " steps today");
        int intoBox = steps % PointsStore.STEPS_PER_BOX;
        int remaining = PointsStore.STEPS_PER_BOX - intoBox;
        int unlocked = Math.min(steps / PointsStore.STEPS_PER_BOX,
                PointsStore.MAX_WALK_BOXES_PER_DAY);
        progressHome.setProgress(unlocked >= PointsStore.MAX_WALK_BOXES_PER_DAY ? 1000 : intoBox);
        progressWalk.setProgress(unlocked >= PointsStore.MAX_WALK_BOXES_PER_DAY ? 1000 : intoBox);
        String toBox = unlocked >= PointsStore.MAX_WALK_BOXES_PER_DAY
                ? "Daily box limit reached (3) — come back tomorrow!"
                : "Walk " + remaining + " more steps to unlock the next Loot Box";
        stepsToBoxHome.setText(toBox);
        stepsToBoxWalk.setText("1,000 steps = 1 Loot Box · max 3 per day · " + toBox);
        refreshBoxes();
    }

    private void refreshBoxes() {
        List<PointsStore.LootBox> boxes = store.getWalkBoxes();
        int unopened = 0;
        for (int i = 0; i < boxButtons.length; i++) {
            Button b = boxButtons[i];
            if (i >= boxes.size()) {
                b.setText("Box " + (i + 1) + " — Locked");
                b.setBackgroundColor(0xFF1E1E1E);
                b.setTextColor(0xFFFFFFFF);
                b.setEnabled(false);
            } else {
                PointsStore.LootBox box = boxes.get(i);
                if (box.opened) {
                    b.setText("Box " + (i + 1) + " — Opened · +" + box.points + " pts");
                    b.setBackgroundColor(0xFF1E1E1E);
                    b.setTextColor(0xFFB0B0B0);
                    b.setEnabled(false);
                } else {
                    b.setText("Box " + (i + 1) + " — " + box.tier.label + " · Tap to open");
                    b.setBackgroundColor(box.tier.color);
                    b.setTextColor(0xFF111111);
                    b.setEnabled(true);
                    unopened++;
                }
            }
        }
        boxesHome.setText(unopened + (unopened == 1 ? " Loot Box" : " Loot Boxes")
                + " waiting to open");
        refreshBonusBoxes();
    }

    private void refreshBonusBoxes() {
        List<PointsStore.LootBox> bonus = store.getBonusBoxes();
        bonusBoxRow.removeAllViews();
        if (bonus.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            empty.setText("No bonus boxes yet — watch an ad from Home to earn one.");
            empty.setTextColor(0xFFB0B0B0);
            empty.setTextSize(14);
            bonusBoxRow.addView(empty);
            return;
        }
        for (int i = 0; i < bonus.size(); i++) {
            final int index = i;
            PointsStore.LootBox box = bonus.get(i);
            Button b = new Button(this);
            b.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            if (box.opened) {
                b.setText("Bonus " + box.tier.label + " — Opened · +" + box.points + " pts");
                b.setBackgroundColor(0xFF1E1E1E);
                b.setTextColor(0xFFB0B0B0);
                b.setEnabled(false);
            } else {
                b.setText("Bonus " + box.tier.label + " · Tap to open");
                b.setBackgroundColor(box.tier.color);
                b.setTextColor(0xFF111111);
                b.setEnabled(true);
                b.setOnClickListener(v -> {
                    int points = store.openBonusBox(index);
                    if (points > 0) {
                        toast("Bonus box opened! +" + points + " pts");
                        maybeShowInterstitial();
                    }
                    refreshAll();
                });
            }
            bonusBoxRow.addView(b);
        }
    }

    private void refreshAll() {
        refreshAccount();
        refreshStepDisplays();
        int balance = store.getBalance();
        String balanceText = balance + " pts";
        headerPoints.setText(balanceText);
        pointsHome.setText(balanceText);
        pointsRewards.setText("Your balance: " + balanceText);
        if (store.hasOpenedLuckyBoxToday()) {
            findViewById(R.id.btnLucky).setEnabled(false);
            ((Button) findViewById(R.id.btnLucky)).setText("Lucky Box opened — come back tomorrow");
        }
        receiptsCount.setText("Receipts today: " + store.getReceiptsToday() + " / 3");
        setList(historyList, store.getHistory());
        setList(receiptHistoryList, filterReceipts(store.getHistory()));
        setList(redemptionList, store.getRedemptions());
    }

    private List<String> filterReceipts(List<String> history) {
        List<String> out = new ArrayList<>();
        for (String line : history) {
            if (line.contains("Receipt")) out.add(line);
        }
        if (out.isEmpty()) out.add("No receipts yet — snap your first one above.");
        return out;
    }

    private void setList(ListView list, List<String> items) {
        List<String> display = items.isEmpty()
                ? new ArrayList<String>() {{ add("Nothing here yet."); }} : items;
        list.setAdapter(new ArrayAdapter<>(this, R.layout.list_row, display));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}

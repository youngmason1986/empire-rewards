package com.blackempire.rewards;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/**
 * Initializes Firebase manually (no google-services plugin) from the
 * Empire Social project config in FirebaseConfig — same pattern as
 * Empire Tester. Manual init is used because the social
 * google-services.json only lists package com.blackempire.social, and
 * the plugin would fail to find a client for com.blackempire.rewards.
 * Auth itself uses the project + API key, not the package, so manual
 * init works — but verified Google sign-in ALSO needs this package +
 * debug SHA-1 registered as an Android app in that Firebase project
 * (done 2026-11-19). Without that registration, Google sign-in fails
 * with a clear error instead of silently signing the user in wrongly.
 */
public class RewardsApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseOptions opts = new FirebaseOptions.Builder()
                        .setProjectId(FirebaseConfig.PROJECT_ID)
                        .setApplicationId(FirebaseConfig.APP_ID)
                        .setApiKey(FirebaseConfig.API_KEY)
                        .setStorageBucket(FirebaseConfig.STORAGE_BUCKET)
                        .build();
                FirebaseApp.initializeApp(this, opts);
            }
        } catch (Exception ignored) {
            // If init fails, MainActivity surfaces the error on sign-in.
        }
    }
}

package com.blackempire.rewards;

import android.app.Activity;
import android.util.Log;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

import java.util.ArrayList;
import java.util.List;

/**
 * Real ads via the Unity Ads SDK (4.20.1, in the offline repo).
 *
 * Live since v2 (2026-11-19): Game ID 800444073, TEST_MODE = false.
 * Three formats:
 *  - Rewarded video (BP_Rewarded_Android): reward only on COMPLETED.
 *  - Banner (Banner_Android): 320x50 at the bottom of the Home screen.
 *  - Interstitial (Interstitial_Android): after opening a Loot Box,
 *    rate-limited by the caller.
 *
 * Every call is guarded so a missing SDK, failed init, or failed load
 * never crashes the app and never grants points for an ad that was not
 * watched. Interstitials never grant points at all.
 */
public final class UnityAdsProvider implements AdProvider {

    private static final String TAG = "EmpireRewardsUnityAds";
    private static boolean initStarted = false;
    private static boolean initCompleted = false;
    private static final List<Runnable> onInitializedCallbacks = new ArrayList<>();

    /** Initialize once at app start. Safe to call repeatedly; no-op here
     * unless a Game ID is configured. */
    public static void initialize(Activity activity) {
        if (initStarted || UnityAdsConfig.GAME_ID == null
                || UnityAdsConfig.GAME_ID.isEmpty()) {
            return;
        }
        initStarted = true;
        try {
            UnityAds.initialize(activity.getApplicationContext(), UnityAdsConfig.GAME_ID,
                    UnityAdsConfig.TEST_MODE, new IUnityAdsInitializationListener() {
                        @Override
                        public void onInitializationComplete() {
                            initCompleted = true;
                            List<Runnable> pending;
                            synchronized (onInitializedCallbacks) {
                                pending = new ArrayList<>(onInitializedCallbacks);
                                onInitializedCallbacks.clear();
                            }
                            for (Runnable r : pending) {
                                try {
                                    r.run();
                                } catch (Throwable ignored) {
                                }
                            }
                            Log.i(TAG, "Unity Ads initialized"
                                    + (UnityAdsConfig.TEST_MODE ? " (TEST MODE)" : " (LIVE)"));
                        }

                        @Override
                        public void onInitializationFailed(
                                UnityAds.UnityAdsInitializationError error, String message) {
                            Log.w(TAG, "Unity Ads init failed: " + error + " / " + message);
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "Unity Ads unavailable, continuing without real ads", t);
        }
    }

    /** Run {@code r} as soon as Unity Ads has finished initializing.
     * Runs immediately if init already completed; never runs if init
     * fails (no network, bad Game ID) — callers must not depend on it. */
    public static void runWhenInitialized(Runnable r) {
        if (initCompleted) {
            r.run();
            return;
        }
        synchronized (onInitializedCallbacks) {
            onInitializedCallbacks.add(r);
        }
    }

    /**
     * Create a 320x50 banner view for the Banner_Android placement and
     * start loading it. Returns null when live ads are not configured
     * or the banner could not be created. The caller adds the view to
     * its container; onBannerLoaded fires {@code onLoaded}, a load
     * failure fires {@code onFailed} (caller hides the container).
     * A failed banner is silent — it never crashes and never blocks
     * the rest of the app.
     */
    public static BannerView createBanner(final Activity activity,
                                          final Runnable onLoaded,
                                          final Runnable onFailed) {
        if (activity == null || UnityAdsConfig.GAME_ID == null
                || UnityAdsConfig.GAME_ID.isEmpty()) {
            return null;
        }
        try {
            final BannerView banner = new BannerView(activity,
                    UnityAdsConfig.BANNER_PLACEMENT_ID, new UnityBannerSize(320, 50));
            banner.setListener(new BannerView.IListener() {
                @Override
                public void onBannerLoaded(BannerView view) {
                    Log.i(TAG, "Banner loaded");
                    if (onLoaded != null) activity.runOnUiThread(onLoaded);
                }

                @Override
                public void onBannerShown(BannerView view) {
                }

                @Override
                public void onBannerClick(BannerView view) {
                }

                @Override
                public void onBannerFailedToLoad(BannerView view, BannerErrorInfo info) {
                    Log.w(TAG, "Banner failed to load: "
                            + (info != null ? info.errorMessage : "unknown"));
                    if (onFailed != null) activity.runOnUiThread(onFailed);
                }

                @Override
                public void onBannerLeftApplication(BannerView view) {
                }
            });
            banner.load();
            return banner;
        } catch (Throwable t) {
            Log.w(TAG, "Banner unavailable", t);
            return null;
        }
    }

    /**
     * Show an interstitial ad. No reward is ever granted for it.
     * {@code onShown} fires when the ad actually starts showing;
     * {@code onFailed} fires when it could not be loaded or shown.
     * Both are best-effort: a failure is silent and never crashes.
     */
    public void showInterstitial(final Activity activity,
                                 final Runnable onShown,
                                 final Runnable onFailed) {
        if (activity == null || activity.isFinishing()) {
            if (onFailed != null) onFailed.run();
            return;
        }
        try {
            UnityAds.load(UnityAdsConfig.INTERSTITIAL_PLACEMENT_ID,
                    new IUnityAdsLoadListener() {
                        @Override
                        public void onUnityAdsAdLoaded(String placementId) {
                            activity.runOnUiThread(() -> {
                                try {
                                    UnityAds.show(activity, placementId,
                                            new IUnityAdsShowListener() {
                                                @Override
                                                public void onUnityAdsShowFailure(String pid,
                                                        UnityAds.UnityAdsShowError error,
                                                        String message) {
                                                    Log.w(TAG, "Interstitial failed to show: "
                                                            + message);
                                                    if (onFailed != null) onFailed.run();
                                                }

                                                @Override
                                                public void onUnityAdsShowStart(String pid) {
                                                    if (onShown != null) onShown.run();
                                                }

                                                @Override
                                                public void onUnityAdsShowClick(String pid) {
                                                }

                                                @Override
                                                public void onUnityAdsShowComplete(String pid,
                                                        UnityAds.UnityAdsShowCompletionState state) {
                                                    // Interstitials never grant points.
                                                }
                                            });
                                } catch (Throwable t) {
                                    if (onFailed != null) onFailed.run();
                                }
                            });
                        }

                        @Override
                        public void onUnityAdsFailedToLoad(String placementId,
                                UnityAds.UnityAdsLoadError error, String message) {
                            Log.w(TAG, "Interstitial failed to load: " + message);
                            activity.runOnUiThread(() -> {
                                if (onFailed != null) onFailed.run();
                            });
                        }
                    });
        } catch (Throwable t) {
            if (onFailed != null) onFailed.run();
        }
    }

    @Override
    public void showRewardedAd(final Activity activity, final RewardCallback callback) {
        if (activity == null || activity.isFinishing()) {
            callback.onAdUnavailable("Activity not available");
            return;
        }
        try {
            UnityAds.load(UnityAdsConfig.REWARDED_PLACEMENT_ID, new IUnityAdsLoadListener() {
                @Override
                public void onUnityAdsAdLoaded(String placementId) {
                    activity.runOnUiThread(() -> {
                        try {
                            UnityAds.show(activity, placementId, new IUnityAdsShowListener() {
                                @Override
                                public void onUnityAdsShowFailure(String pid,
                                        UnityAds.UnityAdsShowError error, String message) {
                                    callback.onAdUnavailable("Ad failed to show: " + message);
                                }

                                @Override
                                public void onUnityAdsShowStart(String pid) {
                                }

                                @Override
                                public void onUnityAdsShowClick(String pid) {
                                }

                                @Override
                                public void onUnityAdsShowComplete(String pid,
                                        UnityAds.UnityAdsShowCompletionState state) {
                                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                        callback.onRewardEarned();
                                    } else {
                                        callback.onAdUnavailable(
                                                "Ad skipped before the end — no reward");
                                    }
                                }
                            });
                        } catch (Throwable t) {
                            callback.onAdUnavailable("Ad failed to show");
                        }
                    });
                }

                @Override
                public void onUnityAdsFailedToLoad(String placementId,
                        UnityAds.UnityAdsLoadError error, String message) {
                    callback.onAdUnavailable("No ad available right now: " + message);
                }
            });
        } catch (Throwable t) {
            callback.onAdUnavailable("Ads unavailable on this device");
        }
    }
}

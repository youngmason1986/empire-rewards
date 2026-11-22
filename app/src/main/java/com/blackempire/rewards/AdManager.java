package com.blackempire.rewards;

import android.app.Activity;

/**
 * Picks the ad provider. Real Unity Ads only when a Game ID for Empire
 * Rewards is configured; otherwise the clearly-labelled test-mode flow.
 * The rest of the app only ever talks to AdProvider.
 */
public final class AdManager {
    private AdManager() {}

    /** True once a Unity Game ID for Empire Rewards has been configured. */
    public static boolean isUnityConfigured() {
        return UnityAdsConfig.GAME_ID != null && !UnityAdsConfig.GAME_ID.isEmpty();
    }

    /** Initialize real ads at app start (no-op until a Game ID is set). */
    public static void initialize(Activity activity) {
        if (isUnityConfigured()) {
            UnityAdsProvider.initialize(activity);
        }
    }

    public static AdProvider provider(TestAdProvider.TestAdScreen testScreen) {
        if (isUnityConfigured()) {
            return new UnityAdsProvider();
        }
        return new TestAdProvider(testScreen);
    }
}

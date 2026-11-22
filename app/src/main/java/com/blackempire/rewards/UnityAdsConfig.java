package com.blackempire.rewards;

/**
 * Unity Ads identifiers for Empire Rewards — LIVE.
 *
 * Unity project "Empire Rewards" (Android), created 2026-11-19 in
 * George's Unity account (odiachigeorge86@gmail.com):
 *   Game ID (Android): 800444073
 *   Rewarded placement: BP_Rewarded_Android (Active)
 *   Banner placement:   Banner_Android (Active)
 *   Interstitial placement: Interstitial_Android (Active)
 *
 * Do NOT reuse the Empire VTU Game ID (800418784) or the Empire Ludo
 * Game ID (800384441) — those belong to those apps, and using one here
 * would credit the wrong project for Rewards ads.
 *
 * TEST_MODE is false, so the app serves real ads and earns real money
 * (per Unity's rates, once real users see them). The test-mode ad
 * screen (TestAdProvider) is now unused.
 */
public final class UnityAdsConfig {
    private UnityAdsConfig() {}

    /** Unity Game ID for Empire Rewards (Android). */
    public static final String GAME_ID = "800444073";

    /** Rewarded-video placement id. */
    public static final String REWARDED_PLACEMENT_ID = "BP_Rewarded_Android";

    /** Banner placement id (bottom of the Home screen). */
    public static final String BANNER_PLACEMENT_ID = "Banner_Android";

    /** Interstitial placement id (after opening a Loot Box, rate-limited). */
    public static final String INTERSTITIAL_PLACEMENT_ID = "Interstitial_Android";

    /** true = test ads only (earns nothing); false = real ads. */
    public static final boolean TEST_MODE = false;
}

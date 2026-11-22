package com.blackempire.rewards;

import android.app.Activity;

/** One way to show a rewarded ad. Two implementations exist: the real
 * Unity Ads one (active only once a Game ID is configured) and the
 * clearly-labelled test-mode one (placeholder screen, used until then). */
public interface AdProvider {

    interface RewardCallback {
        /** The user watched the ad to the end — grant the reward. */
        void onRewardEarned();

        /** No ad could be shown (not loaded, no network, SDK missing). */
        void onAdUnavailable(String reason);
    }

    /** Show a rewarded ad. Exactly one callback method will fire. */
    void showRewardedAd(Activity activity, RewardCallback callback);
}

package com.blackempire.rewards;

import android.app.Activity;

/**
 * Test-mode rewarded ad. Used while no Unity Game ID is configured for
 * Empire Rewards. It does NOT pretend to be a real ad: the activity shows
 * a full-screen overlay clearly labelled "TEST AD", counts down, and only
 * then grants the reward — so the earn flow can be tested end to end
 * without a fake advertiser or fake earnings.
 */
public final class TestAdProvider implements AdProvider {

    /** Implemented by MainActivity: shows the test-ad overlay, then runs
     * the given runnable when the (simulated) ad finishes. */
    public interface TestAdScreen {
        void showTestAdScreen(Runnable onFinished);
    }

    private final TestAdScreen screen;

    public TestAdProvider(TestAdScreen screen) {
        this.screen = screen;
    }

    @Override
    public void showRewardedAd(Activity activity, final RewardCallback callback) {
        if (screen == null) {
            callback.onAdUnavailable("Test ad screen not available");
            return;
        }
        screen.showTestAdScreen(new Runnable() {
            @Override
            public void run() {
                callback.onRewardEarned();
            }
        });
    }
}

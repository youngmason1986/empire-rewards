package com.blackempire.rewards;

/** Firebase client config for Empire Rewards.
 *
 * Copied from Empire Tester's FirebaseConfig (same project,
 * empire-social-9e540) — these are Firebase *client* identifiers (the
 * same kind the google-services plugin generates into an app), not
 * server secrets. The only value that differs from Tester is APP_ID:
 * this is the Android app com.blackempire.rewards, registered in the
 * Firebase project with the debug SHA-1 (see README).
 *
 * Google sign-in also needs this package + debug SHA-1 registered as an
 * Android app in that Firebase project — done 2026-11-19 (App ID below).
 * Without that registration, Google sign-in fails with a clear error. */
public final class FirebaseConfig {
    public static final String PROJECT_ID = "empire-social-9e540";
    public static final String STORAGE_BUCKET = "empire-social-9e540.firebasestorage.app";
    public static final String API_KEY = "AIzaSyBwLRO8tZSOMTav6FfUeojgQgvN2If3DUk";
    public static final String APP_ID = "1:353189477485:android:3a0a0dc3ec428991075364";
    public static final String WEB_CLIENT_ID = "353189477485-v6nntkn20uu44lnmops2b2k63t89kuj9.apps.googleusercontent.com";
    private FirebaseConfig() {}
}

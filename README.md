# Empire Rewards — com.blackempire.rewards

Rewards app modelled on Sampo (walk-to-earn) + Mani (receipt rewards).
Black-and-gold Black Empire styling. Single activity, four screens
(Home / Walk / Receipts / Rewards). All state is local
(SharedPreferences) — no account, no server.

## What works in v2 (2026-11-19) — LIVE ads

Everything in v1, plus real Unity Ads (see "Ads" below):

- **Banner:** a 320x50 Unity banner at the bottom of the Home screen
  only, in a reserved 50dp strip so it never covers any button. The
  strip stays hidden until a banner actually loads, and stays hidden
  on the Walk / Receipts / Rewards screens.
- **Interstitial:** shown after the user opens a Loot Box, rate-limited
  — at most once every 2 minutes, never on two box opens in a row,
  never while another interstitial is loading. A failed load/show is
  silent. Interstitials never grant points.

- **Walk-to-earn:** TYPE_STEP_COUNTER sensor. Every 1,000 steps unlocks
  a Loot Box (max 3/day, resets at midnight). Bronze 50–120 pts,
  Silver 150–350, Gold 400–900. Tap a box to open it.
- **Receipt rewards:** snap a receipt with the camera, +50 pts each,
  max 3/day. v1 has NO OCR/verification backend — points are awarded
  on submission and every receipt is marked "Pending review". Nothing
  in the app claims a human or system verified a receipt.
- **Daily Lucky Box:** one free box per day, 25–150 pts.
- **Rewarded ads:** +50 pts per watched ad, or one bonus Loot Box
  (max 3 bonus boxes/day). See "Ads" below.
- **Points wallet:** balance + full points history on Home.
- **Rewards screen:** $1 / $5 / $10 / $20 gift cards at 1,000 pts = $1.
  Tapping Redeem stores the request on the phone only and shows
  "Redemption opens once the rewards partner is connected" — the app
  NEVER promises a payout it cannot fulfil. Real fulfilment
  (e.g. Tremendous) needs a backend and funding — out of scope for v1.

## Gift cards — v5 (2026-11-19, George's rule)

George's rule (2026-11-19): **points convert to gift cards at
1,000 points = $1; when a user reaches 1,000 points they pick any
kind of gift card; ad points and walking points both count** toward
the same redeemable total.

- Denominations: **$1 / $5 / $10 / $15 / $20 / $50 / $100** (1,000 /
  5,000 / 10,000 / 15,000 / 20,000 / 50,000 / 100,000 pts). The
  Rewards screen says "Redeeming opens at 1,000 points" and the $1
  row works at exactly 1,000 points.
- Brand picker: tapping Redeem on any denomination first asks for the
  brand — **Amazon, Google Play, Apple, Steam, PlayStation, Xbox** —
  then confirms brand + amount + the signed-in user's email. The
  saved request shows brand, amount and email in "My redemption
  requests".
- Guests must sign in to redeem: a guest tapping Redeem gets a plain
  message and is taken to the sign-in screen (never a crash).
- Points rule verified in code (v5): every earning path writes to the
  SAME balance (`PointsStore.addPoints` → the one `balance` key in
  the owner-scoped prefs file) that the Rewards screen shows and that
  redemptions spend from — rewarded ads (+50), ad bonus boxes
  (opened for their tier points), walking loot boxes, receipts (+50)
  and the daily Lucky Box (25–150). No earning path uses a separate
  counter; nothing needed fixing.
- Honest wording kept: tapping Redeem saves a request on this phone
  only; requests are reviewed and paid from ad revenue. The app
  NEVER promises an instant payout, an instant code, or real Bitcoin.

## Sign-in — v3 (2026-11-19)

Firebase Auth, project **empire-social-9e540** (Empire Social), manual
`FirebaseOptions` init in `RewardsApp` — same pattern as Empire Tester
(no google-services plugin). Android app **com.blackempire.rewards**
registered in the project (App ID
`1:353189477485:android:3a0a0dc3ec428991075364`, debug SHA-1 added).

- Providers enabled: **Google** and **Email/Password** (email link off).
- First launch shows a sign-in screen: "Continue with Google", an
  email + password form with a Sign up / Sign in toggle, and "Continue
  without an account" (guest). Email verification is sent on sign-up
  but never blocks entry in v1.
- Home shows who is signed in (name/email or "Guest") with a Sign out
  option; signing out returns to the sign-in screen.
- Points stay LOCAL in v1 (SharedPreferences), keyed by the signed-in
  user's UID when signed in — two accounts on one phone never share
  points. Guest points use the original v1 prefs file, so v1/v2 guest
  points are preserved. There is no cloud sync yet and the app never
  claims one.
- Errors are plain text on the sign-in screen (wrong password, email
  already in use, no network, Google cancelled / app not registered)
  — never a crash, never a silent sign-in as someone else.

## Logo — v4 (2026-11-19)

George did not like the original gold-coin launcher icon. He picked
**Option 3 — the gold trophy cup with a stack of gold coins on a
black background** (chosen 2026-11-19). The trophy artwork
(`logo-options/media-generation-empire-rewards-logo-trophy-*.webp`,
1600x1600) was downscaled with PIL (Lanczos) into
`ic_launcher.png` + `ic_launcher_round.png` at all five densities
(mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192). The manifest
now also sets `android:roundIcon="@mipmap/ic_launcher_round"`. No
adaptive-icon XML exists — the PNGs are used directly, and the baked
black background blends with the launcher. The other two options
(gift box, R monogram) are kept in `logo-options/` if he ever wants
to switch.

## Ads — LIVE since v2 (2026-11-19)

Unity project "Empire Rewards" (Android) in George's Unity account
(odiachigeorge86@gmail.com), created 2026-11-19:
- **Game ID: 800444073** — `UnityAdsConfig.GAME_ID`
- **Rewarded placement: BP_Rewarded_Android** (+50 pts per completed ad,
  or one bonus Loot Box, max 3/day; reward only on COMPLETED)
- **Banner placement: Banner_Android** (Home screen, 320x50)
- **Interstitial placement: Interstitial_Android** (after Loot Box,
  rate-limited — see above)
- **TEST_MODE = false** — real ads serve and real money is earned
  (per Unity's rates, once real users see them). The VTU Game ID
  (800418784) and Ludo Game ID (800384441) were NOT reused.

The test-mode flow (`TestAdProvider`, the clearly-labelled "TEST AD"
overlay) is still in the app as a fallback but is unused while a
Game ID is configured — `AdManager` picks `UnityAdsProvider` when
`GAME_ID` is set. Every ad call is guarded: a failed init/load/show
never crashes the app and never grants points for an ad not watched.

To change the ads later, edit the IDs in `UnityAdsConfig.java` and
rebuild (same command as below).

## Build

- minSdk 24, compileSdk/targetSdk 36, AGP 8.7.3 (pinned), deps:
  Unity Ads 4.20.1 + firebase-auth 24.0.1 + play-services-auth 21.3.0
  (all in the offline local Maven repo). Framework-only Java (no
  AndroidX in app code).
- Permanent debug keystore `~/workspace/debug.keystore`
  (android/androiddebugkey/android) — explicit signingConfigs.debug.
  APK SHA-1: c1c879f38e5bea2debe32e4b0e7427e8d0608406.
- Build: `ANDROID_HOME=~/workspace/android-sdk JAVA_HOME=~/workspace/jdk-17
  GRADLE_USER_HOME=~/.gradle-empire-rewards ~/workspace/gradle-8.9/bin/gradle
  assembleDebug --offline -Djava.net.preferIPv4Stack=true
  -Dorg.gradle.jvmargs="-Djava.net.preferIPv4Stack=true -Xmx2g"`

## GitHub

Source lives at https://github.com/youngmason1986/empire-rewards (public).
Every push to `main` builds the debug APK automatically with GitHub
Actions (JDK 17, Android SDK, `./gradlew assembleDebug`) and attaches
the APK as a build artifact — no local Android Studio needed.

- The Gradle wrapper (`gradlew`) is committed, so CI uses Gradle 8.9.
- `settings.gradle` uses Google Maven + Maven Central first, and the
  local offline repo only when it exists (Brain's Linux) — so the same
  project builds both locally (offline) and on CI (online).
- The permanent debug keystore (`~/workspace/debug.keystore`) is used
  only when the file exists (local builds). On CI, Gradle falls back to
  its default debug signing — Google Sign-In on a CI-built APK needs
  that keystore's SHA-1 added in Firebase before it will work.
- The Firebase API key and Unity Game ID in the code are client IDs
  (already inside the published APK) — no server secrets are in the
  repo. Never commit a keystore (see `.gitignore`).

## Download

https://blackempireservices.name.ng/downloads/empire-rewards-debug.apk
(v5 debug APK, 8,231,194 bytes, versionCode 5 / versionName 1.4;
deployment 0eb78d93-e05d-4c2c-ab8b-4ad7419222d8; live file verified
by md5 match 450679d40d0e0808d09061c22d224de3)

## On-phone test still needed (George)

Install, allow Physical activity permission, walk and watch boxes
unlock; snap a receipt; open the Lucky Box; watch a real rewarded ad;
try a redemption with too few points and with enough points. Check the
banner shows at the bottom of Home (and nowhere else) and that an
interstitial appears after opening a box — at most once every
2 minutes. NEW in v3: sign in with Google (tap "Continue with
Google", pick your account), sign up with email + password (then sign
out and sign back in), and continue as a guest — check the account
label on the Home screen changes, and that points earned while signed
in stay separate from guest points. NEW in v5: redeem a $1 card at
exactly 1,000 points, pick a brand in the picker, check the request
shows brand + amount + your email; as a guest, tapping Redeem must
ask you to sign in first.

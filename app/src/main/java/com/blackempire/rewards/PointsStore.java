package com.blackempire.rewards;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * All Empire Rewards state lives in SharedPreferences — no server, no
 * account, no network. Daily counters are keyed by date (yyyy-MM-dd) so
 * they reset naturally at midnight.
 */
public final class PointsStore {

    private static final String PREFS = "empire_rewards";

    private static final String KEY_BALANCE = "balance";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_REDEMPTIONS = "redemptions";
    private static final String KEY_STEPS_SEEN = "steps_seen";

    /** Max loot boxes unlockable from walking per day. */
    public static final int MAX_WALK_BOXES_PER_DAY = 3;
    /** Steps needed to unlock one loot box. */
    public static final int STEPS_PER_BOX = 1000;
    /** Points for one accepted receipt (v1: awarded on submission). */
    public static final int POINTS_PER_RECEIPT = 50;
    /** Max receipts per day. */
    public static final int MAX_RECEIPTS_PER_DAY = 3;
    /** Points for watching one rewarded ad. */
    public static final int POINTS_PER_AD = 50;
    /** Max bonus boxes earnable from ads per day. */
    public static final int MAX_BONUS_BOXES_PER_DAY = 3;
    /** Points per $1 of gift card. */
    public static final int POINTS_PER_DOLLAR = 1000;

    private SharedPreferences prefs;
    private final Context appContext;
    private final Random random = new Random();

    public PointsStore(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Switch the points owner. A Firebase UID scopes all points, boxes,
     * receipts and redemptions to that account (a second account on the
     * same phone starts from zero and never sees the first account's
     * points). Null = guest, which uses the original v1 prefs file, so
     * guest points from v1/v2 are preserved. Nothing here is cloud
     * synced — points stay on this phone in v1.
     */
    public void setOwner(String ownerId) {
        String name = ownerId == null ? PREFS : PREFS + "_" + ownerId;
        prefs = appContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /** Today's date key, e.g. "2026-11-18". */
    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String stamp() {
        return new SimpleDateFormat("dd MMM HH:mm", Locale.US).format(new Date());
    }

    // ---- Points balance & history ----

    public int getBalance() {
        return prefs.getInt(KEY_BALANCE, 0);
    }

    /** Add points and record the reason in history. Returns the new balance. */
    public int addPoints(int points, String reason) {
        int balance = getBalance() + points;
        prefs.edit().putInt(KEY_BALANCE, balance).apply();
        addHistoryEntry("+" + points + " pts · " + reason);
        return balance;
    }

    /** Spend points (for a redemption request). Returns false if not enough. */
    public boolean spendPoints(int points, String reason) {
        if (getBalance() < points) return false;
        int balance = getBalance() - points;
        prefs.edit().putInt(KEY_BALANCE, balance).apply();
        addHistoryEntry("-" + points + " pts · " + reason);
        return balance >= 0;
    }

    public List<String> getHistory() {
        String raw = prefs.getString(KEY_HISTORY, "");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                if (!line.trim().isEmpty()) out.add(line);
            }
        }
        return out;
    }

    private void addHistoryEntry(String entry) {
        List<String> history = getHistory();
        history.add(0, stamp() + " · " + entry);
        while (history.size() > 100) history.remove(history.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String line : history) sb.append(line).append("\n");
        prefs.edit().putString(KEY_HISTORY, sb.toString()).apply();
    }

    // ---- Steps ----

    /**
     * Feed a TYPE_STEP_COUNTER reading. Returns today's step count.
     * The sensor counts since boot, so we store the first reading of the
     * day as the baseline and subtract it.
     */
    public int onStepCounter(long totalSinceBoot) {
        String key = "steps_baseline_" + todayKey();
        long baseline = prefs.getLong(key, -1);
        if (baseline < 0) {
            prefs.edit().putLong(key, totalSinceBoot).apply();
            baseline = totalSinceBoot;
        }
        prefs.edit().putLong(KEY_STEPS_SEEN, totalSinceBoot).apply();
        long today = totalSinceBoot - baseline;
        return today < 0 ? 0 : (int) today;
    }

    /** Today's steps from the last seen counter value (0 if never seen). */
    public int getStepsToday() {
        String key = "steps_baseline_" + todayKey();
        long baseline = prefs.getLong(key, -1);
        if (baseline < 0) return 0;
        long seen = prefs.getLong(KEY_STEPS_SEEN, baseline);
        long today = seen - baseline;
        return today < 0 ? 0 : (int) today;
    }

    // ---- Loot boxes ----

    /** One loot box: a tier, its point value, and whether it was opened. */
    public static final class LootBox {
        public final BoxTier tier;
        public final int points;
        public boolean opened;

        public LootBox(BoxTier tier, int points, boolean opened) {
            this.tier = tier;
            this.points = points;
            this.opened = opened;
        }

        String serialize() {
            return tier.name() + ":" + points + ":" + (opened ? 1 : 0);
        }

        static LootBox parse(String s) {
            try {
                String[] p = s.split(":");
                return new LootBox(BoxTier.fromName(p[0]),
                        Integer.parseInt(p[1]), "1".equals(p[2]));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private List<LootBox> loadBoxes(String key) {
        List<LootBox> out = new ArrayList<>();
        String raw = prefs.getString(key, "");
        if (!raw.isEmpty()) {
            for (String part : raw.split(";")) {
                LootBox b = LootBox.parse(part);
                if (b != null) out.add(b);
            }
        }
        return out;
    }

    private void saveBoxes(String key, List<LootBox> boxes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < boxes.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(boxes.get(i).serialize());
        }
        prefs.edit().putString(key, sb.toString()).apply();
    }

    /**
     * Walk boxes for today. Unlocks one box per 1,000 steps, up to the
     * daily max. New boxes are generated as the user walks.
     */
    public List<LootBox> getWalkBoxes() {
        String key = "boxes_" + todayKey();
        List<LootBox> boxes = loadBoxes(key);
        int unlocked = Math.min(getStepsToday() / STEPS_PER_BOX, MAX_WALK_BOXES_PER_DAY);
        while (boxes.size() < unlocked) {
            BoxTier tier = BoxTier.rollTier(random);
            boxes.add(new LootBox(tier, tier.rollPoints(random), false));
        }
        saveBoxes(key, boxes);
        return boxes;
    }

    /** Mark a walk box opened and grant its points. Returns points granted. */
    public int openWalkBox(int index) {
        String key = "boxes_" + todayKey();
        List<LootBox> boxes = loadBoxes(key);
        if (index < 0 || index >= boxes.size()) return 0;
        LootBox box = boxes.get(index);
        if (box.opened) return 0;
        box.opened = true;
        saveBoxes(key, boxes);
        addPoints(box.points, "Opened " + box.tier.label + " loot box");
        return box.points;
    }

    /** How many walk boxes are unlocked but not yet opened. */
    public int countUnopenedWalkBoxes() {
        int n = 0;
        for (LootBox b : getWalkBoxes()) if (!b.opened) n++;
        return n;
    }

    // ---- Bonus boxes (from rewarded ads) ----

    public List<LootBox> getBonusBoxes() {
        return loadBoxes("bonus_" + todayKey());
    }

    /** Add one bonus box (from a watched ad). Returns false at the daily cap. */
    public boolean addBonusBox() {
        String key = "bonus_" + todayKey();
        List<LootBox> boxes = loadBoxes(key);
        if (boxes.size() >= MAX_BONUS_BOXES_PER_DAY) return false;
        BoxTier tier = BoxTier.rollTier(random);
        boxes.add(new LootBox(tier, tier.rollPoints(random), false));
        saveBoxes(key, boxes);
        return true;
    }

    /** Open a bonus box. Returns points granted (0 if already opened). */
    public int openBonusBox(int index) {
        String key = "bonus_" + todayKey();
        List<LootBox> boxes = loadBoxes(key);
        if (index < 0 || index >= boxes.size()) return 0;
        LootBox box = boxes.get(index);
        if (box.opened) return 0;
        box.opened = true;
        saveBoxes(key, boxes);
        addPoints(box.points, "Opened bonus " + box.tier.label + " box (ad reward)");
        return box.points;
    }

    // ---- Lucky Box (one free box per day) ----

    public boolean hasOpenedLuckyBoxToday() {
        return prefs.getBoolean("lucky_" + todayKey(), false);
    }

    /** Open the daily Lucky Box. Returns points, or -1 if already opened. */
    public int openLuckyBox() {
        if (hasOpenedLuckyBoxToday()) return -1;
        prefs.edit().putBoolean("lucky_" + todayKey(), true).apply();
        int points = 25 + random.nextInt(126); // 25–150
        addPoints(points, "Daily Lucky Box");
        return points;
    }

    // ---- Receipts ----

    public int getReceiptsToday() {
        return prefs.getInt("receipts_" + todayKey(), 0);
    }

    /** Record an accepted receipt. Returns false at the daily cap. */
    public boolean addReceipt() {
        int count = getReceiptsToday();
        if (count >= MAX_RECEIPTS_PER_DAY) return false;
        prefs.edit().putInt("receipts_" + todayKey(), count + 1).apply();
        addPoints(POINTS_PER_RECEIPT,
                "Receipt #" + (count + 1) + " accepted (Pending review)");
        return true;
    }

    // ---- Redemption requests (local only in v1) ----

    public List<String> getRedemptions() {
        String raw = prefs.getString(KEY_REDEMPTIONS, "");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                if (!line.trim().isEmpty()) out.add(line);
            }
        }
        return out;
    }

    public void addRedemption(String text) {
        List<String> list = getRedemptions();
        list.add(0, stamp() + " · " + text);
        StringBuilder sb = new StringBuilder();
        for (String line : list) sb.append(line).append("\n");
        prefs.edit().putString(KEY_REDEMPTIONS, sb.toString()).apply();
    }
}

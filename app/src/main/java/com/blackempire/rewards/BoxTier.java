package com.blackempire.rewards;

import java.util.Random;

/** Loot-box tiers, Sampo-style. Points are awarded when the box is opened. */
public enum BoxTier {
    BRONZE("Bronze", 50, 120, 0xFFCD7F32),
    SILVER("Silver", 150, 350, 0xFFC0C0C0),
    GOLD("Gold", 400, 900, 0xFFFFD700);

    public final String label;
    public final int minPoints;
    public final int maxPoints;
    /** ARGB colour used for the box button. */
    public final int color;

    BoxTier(String label, int minPoints, int maxPoints, int color) {
        this.label = label;
        this.minPoints = minPoints;
        this.maxPoints = maxPoints;
        this.color = color;
    }

    /** Roll points for this tier. */
    public int rollPoints(Random random) {
        return minPoints + random.nextInt(maxPoints - minPoints + 1);
    }

    /** Weighted roll: Bronze 60%, Silver 30%, Gold 10%. */
    public static BoxTier rollTier(Random random) {
        int r = random.nextInt(100);
        if (r < 60) return BRONZE;
        if (r < 90) return SILVER;
        return GOLD;
    }

    public static BoxTier fromName(String name) {
        for (BoxTier t : values()) {
            if (t.name().equals(name)) return t;
        }
        return BRONZE;
    }
}

package com.gtnhblueprints.machine;

/** Arithmetic for a single shared sixteen-hatch bank, with native int fluid API limits. */
public final class HostedAntimatterBatch {

    public static final int HATCHES = 16;

    private HostedAntimatterBatch() {}

    public static long scaleCost(long amount, int machines) {
        if (amount <= 0) return 0L;
        int count = Math.max(1, machines);
        return amount > Long.MAX_VALUE / count ? Long.MAX_VALUE : amount * count;
    }

    public static int scaleChange(int amount, int machines) {
        long scaled = (long) amount * Math.max(1, machines);
        // Native checkProcessing sums sixteen signed changes and takes Math.abs(int) for protomatter.
        long limit = Integer.MAX_VALUE / HATCHES;
        return (int) Math.max(-limit, Math.min(limit, scaled));
    }
}

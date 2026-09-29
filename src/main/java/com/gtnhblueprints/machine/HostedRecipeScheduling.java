package com.gtnhblueprints.machine;

final class HostedRecipeScheduling {
    private HostedRecipeScheduling() {}

    static long powerShare(long available, int recipes, long minimum) {
        if (available <= 0) return 0;
        return Math.min(available, Math.max(Math.max(1L, minimum), available / Math.max(1, recipes)));
    }
}

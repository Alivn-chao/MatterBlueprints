package com.gtnhblueprints.machine;

import java.util.ArrayList;
import java.util.List;

/** Shares assembly units between runnable recipes, then fills unused capacity from live inputs again. */
final class HostedAssemblyScheduling {
    private HostedAssemblyScheduling() {}

    @FunctionalInterface
    interface Attempt<T> {
        /** Returns occupied units (0 if unavailable). Probes must not consume inputs or modify jobs. */
        int run(T recipe, int units);
    }

    static <T> boolean schedule(List<T> recipes, int units, int slots, Attempt<T> probe, Attempt<T> start) {
        boolean started = false;
        while (units > 0 && slots > 0) {
            List<T> ready = new ArrayList<>();
            List<Integer> demands = new ArrayList<>();
            for (T recipe : recipes) {
                int demand = Math.min(units, probe.run(recipe, units));
                if (demand <= 0) continue;
                ready.add(recipe);
                demands.add(demand);
                if (ready.size() >= slots) break;
            }
            if (ready.isEmpty()) break;
            int[] shares = shares(units, demands);
            boolean progressed = false;
            for (int i = 0; i < ready.size() && slots > 0; i++) {
                if (shares[i] <= 0) continue;
                // A probe is only a hint: earlier starts may consume shared stock or reserve wired power.
                int occupied = start.run(ready.get(i), Math.min(units, shares[i]));
                if (occupied <= 0) continue;
                if (occupied > shares[i]) throw new IllegalStateException("Assembly exceeded allocated units");
                units -= occupied;
                slots--;
                progressed = true;
                started = true;
            }
            if (!progressed) {
                // Some recipes need several units' combined power just to start. Equal shares can be too small.
                for (T recipe : ready) {
                    int occupied = start.run(recipe, units);
                    if (occupied <= 0) continue;
                    if (occupied > units) throw new IllegalStateException("Assembly exceeded available units");
                    units -= occupied;
                    slots--;
                    progressed = true;
                    started = true;
                    break;
                }
                if (!progressed) break;
            }
            // Do not exclude an already running recipe. Another idle controller/buffer can do the same work.
            // Each successful pass consumes at least one slot, bounding retries even with virtual ME stock.
        }
        return started;
    }

    static int[] shares(int units, List<Integer> demands) {
        int[] result = new int[demands.size()];
        if (units <= 0 || demands.isEmpty()) return result;
        // Capped equal shares: a recipe with only one unit of ingredients must not strand the other shares.
        int low = 0, high = units;
        while (low < high) {
            int level = (int) (((long) low + high + 1) / 2);
            long sum = 0;
            for (int demand : demands) sum += Math.min(level, Math.max(0, demand));
            if (sum <= units) low = level;
            else high = level - 1;
        }
        long used = 0;
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.min(low, Math.max(0, demands.get(i)));
            used += result[i];
        }
        for (int i = 0; i < result.length && used < units; i++) {
            if (result[i] < demands.get(i)) {
                result[i]++;
                used++;
            }
        }
        return result;
    }
}

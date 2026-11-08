package com.gtnhblueprints.machine;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Calculation-only benchmark. No Minecraft worlds, recipes, tile ticks or network; NOT an MSPT measurement. */
public final class Hosting128Benchmark {
    static class Base { private long value = 71; }
    static class Machine extends Base {}
    static volatile long sink;
    static final Machine[] MACHINES = new Machine[128];
    static final List<Integer> PRIMARY = new ArrayList<>(), EXTRA = new ArrayList<>();
    static final Iterable<Integer> VIEW = new HostedCombinedView<>(PRIMARY, EXTRA, item -> true, item -> item);
    static {
        for (int i = 0; i < 128; i++) { MACHINES[i] = new Machine(); PRIMARY.add(i); EXTRA.add(i + 128); }
    }
    static Field oldField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new AssertionError();
    }
    static void run(boolean cached, boolean fields) throws Exception {
        long total = 0;
        if (fields) {
            for (int t = 0; t < 200; t++) for (Machine machine : MACHINES) {
                Field field = cached ? HostedFieldCache.get(machine.getClass(), "value") : oldField(machine.getClass(), "value");
                total += field.getLong(machine);
            }
        } else {
            for (int t = 0; t < 20000; t++) {
                Iterable<Integer> tasks;
                if (cached) tasks = VIEW;
                else {
                    List<Integer> copy = new ArrayList<>(PRIMARY);
                    for (Integer item : EXTRA) copy.add(item);
                    tasks = copy;
                }
                for (int task : tasks) total += task;
            }
        }
        sink = total;
    }
    public static void main(String[] args) throws Exception {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        for (boolean fields : new boolean[] { true, false }) {
            for (int i = 0; i < 4; i++) { run(false, fields); run(true, fields); }
            for (boolean cached : new boolean[] { false, true }) {
                long[] times = new long[7], allocations = new long[7];
                for (int i = 0; i < times.length; i++) {
                    long thread = Thread.currentThread().getId();
                    long startBytes = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
                    run(cached, fields);
                    times[i] = System.nanoTime() - start;
                    allocations[i] = bean.getThreadAllocatedBytes(thread) - startBytes;
                }
                Arrays.sort(times); Arrays.sort(allocations);
                System.out.printf(java.util.Locale.ROOT, "%s %s: %.3f ms, %.3f MB, checksum=%d%n",
                    fields ? "128-machine field reads / 200 rounds" : "128+128-task traversal / 20000 rounds",
                    cached ? "after" : "before", times[3] / 1e6, allocations[3] / 1e6, sink);
            }
        }
    }
}

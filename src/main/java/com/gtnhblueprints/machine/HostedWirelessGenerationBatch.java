package com.gtnhblueprints.machine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Reusable, same-tick generation transaction. Never carries unpaid progress into a later tick. */
final class HostedWirelessGenerationBatch<T> {
    private final List<T> machines = new ArrayList<>();
    private final List<BigInteger> amounts = new ArrayList<>();
    private BigInteger total = BigInteger.ZERO;

    void add(T machine, BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative wireless generation");
        machines.add(machine);
        amounts.add(amount);
        total = total.add(amount);
    }

    boolean flush(Predicate<BigInteger> deposit, BiConsumer<T, BigInteger> commit, Consumer<T> afterCommit) {
        if (machines.isEmpty()) return false;
        try {
            if (total.signum() > 0 && !deposit.test(total)) return false;
            // The commit callback only updates prepaid cycles/counters, with no native/world callbacks.
            // Commit ALL energy before pollution/output hooks: a hook throwing must not leave already credited
            // energy in another machine's cycle where it could be credited again on the next tick.
            for (int i = 0; i < machines.size(); i++) commit.accept(machines.get(i), amounts.get(i));
            for (T machine : machines) afterCommit.accept(machine);
            return true;
        } finally {
            clear();
        }
    }

    void clear() {
        machines.clear();
        amounts.clear();
        total = BigInteger.ZERO;
    }
}

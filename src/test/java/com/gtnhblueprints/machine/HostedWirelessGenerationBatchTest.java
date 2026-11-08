package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class HostedWirelessGenerationBatchTest {
    private HostedNaquadahCycle paid(BigInteger energy) {
        HostedNaquadahCycle cycle = new HostedNaquadahCycle(1, 1, 1);
        cycle.energy = energy;
        cycle.configure(true);
        return cycle;
    }

    @Test
    void oneHundredTwentyEightReactorsUseOneLedgerWritePerTickAndPreserveEveryEu() {
        HostedWirelessGenerationBatch<HostedNaquadahCycle> batch = new HostedWirelessGenerationBatch<>();
        List<HostedNaquadahCycle> reactors = new ArrayList<>();
        BigInteger expected = BigInteger.ZERO;
        for (int i = 0; i < 128; i++) {
            BigInteger energy = BigInteger.TEN.pow(30).add(BigInteger.valueOf(713L * i));
            reactors.add(paid(energy));
            expected = expected.add(energy);
        }
        AtomicReference<BigInteger> ledger = new AtomicReference<>(BigInteger.ZERO);
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        for (int tick = 0; tick < 1200; tick++) {
            for (HostedNaquadahCycle cycle : reactors) batch.add(cycle, cycle.nextOutput(0));
            assertTrue(batch.flush(amount -> {
                writes.incrementAndGet();
                ledger.set(ledger.get().add(amount));
                return true;
            }, HostedNaquadahCycle::accept, cycle -> callbacks.incrementAndGet()));
        }
        assertEquals(1200, writes.get()); // No per-reactor ledger writes, even at 128 machines.
        assertEquals(128 * 1200, callbacks.get());
        assertEquals(expected, ledger.get());
        for (HostedNaquadahCycle cycle : reactors) assertTrue(cycle.finished());
    }

    @Test
    void rejectedDepositAdvancesNoCycleAndDoesNotLeaveRequestsForTheNextTick() {
        HostedWirelessGenerationBatch<HostedNaquadahCycle> batch = new HostedWirelessGenerationBatch<>();
        HostedNaquadahCycle cycle = paid(BigInteger.valueOf(12000));
        batch.add(cycle, cycle.nextOutput(0));
        assertFalse(batch.flush(amount -> false, HostedNaquadahCycle::accept, unused -> fail("Unpaid hook")));
        assertEquals(1200, cycle.wirelessTicks);
        assertEquals(BigInteger.valueOf(12000), cycle.energy);
        assertFalse(batch.flush(amount -> { fail("Old request retained"); return true; },
            HostedNaquadahCycle::accept, unused -> fail("Old request retained")));
        batch.add(cycle, cycle.nextOutput(0));
        assertTrue(batch.flush(amount -> true, HostedNaquadahCycle::accept, unused -> {}));
        assertEquals(1199, cycle.wirelessTicks);
        assertEquals(BigInteger.valueOf(11990), cycle.energy);
    }

    @Test
    void staggeredReloadedReactorsKeepIndependentClocksWhenOneWholeTickIsBlocked() {
        HostedWirelessGenerationBatch<HostedNaquadahCycle> batch = new HostedWirelessGenerationBatch<>();
        List<HostedNaquadahCycle> reactors = new ArrayList<>();
        BigInteger expected = BigInteger.ZERO;
        for (int i = 0; i < 32; i++) {
            HostedNaquadahCycle cycle = paid(BigInteger.TEN.pow(25).add(BigInteger.valueOf(i * 713L)));
            for (int tick = 0; tick < i * 13; tick++) cycle.accept(cycle.nextOutput(0));
            cycle = HostedNaquadahCycle.read(cycle.write());
            reactors.add(cycle);
            expected = expected.add(cycle.energy);
        }
        AtomicReference<BigInteger> ledger = new AtomicReference<>(BigInteger.ZERO);
        for (int frame = 0; frame < 1201; frame++) {
            boolean blocked = frame == 100;
            for (HostedNaquadahCycle cycle : reactors) {
                if (cycle.finished()) continue;
                BigInteger amount = cycle.energy.divide(BigInteger.valueOf(cycle.wirelessTicks));
                assertEquals(amount, cycle.nextOutput(0));
                batch.add(cycle, amount);
            }
            batch.flush(amount -> {
                if (blocked) return false;
                ledger.set(ledger.get().add(amount));
                return true;
            }, HostedNaquadahCycle::accept, unused -> {});
        }
        assertEquals(expected, ledger.get());
        for (HostedNaquadahCycle cycle : reactors) assertTrue(cycle.finished());
    }

    @Test
    void hookFailureCannotRecreditAnotherAlreadyPaidCycle() {
        HostedWirelessGenerationBatch<HostedNaquadahCycle> batch = new HostedWirelessGenerationBatch<>();
        HostedNaquadahCycle first = paid(BigInteger.valueOf(1200));
        HostedNaquadahCycle second = paid(BigInteger.valueOf(2400));
        batch.add(first, first.nextOutput(0));
        batch.add(second, second.nextOutput(0));
        assertThrows(IllegalStateException.class, () -> batch.flush(amount -> {
            assertEquals(BigInteger.valueOf(3), amount);
            return true;
        }, HostedNaquadahCycle::accept, cycle -> { throw new IllegalStateException("Native output hook failed"); }));
        assertEquals(BigInteger.valueOf(1199), first.energy);
        assertEquals(BigInteger.valueOf(2398), second.energy);
        assertEquals(1199, first.wirelessTicks);
        assertEquals(1199, second.wirelessTicks);
        assertFalse(batch.flush(amount -> { fail("Duplicate deposit"); return true; },
            HostedNaquadahCycle::accept, unused -> {}));
    }

    @Test
    void zeroOutputTicksStillAdvanceWithoutWritingTheLedger() {
        HostedWirelessGenerationBatch<HostedNaquadahCycle> batch = new HostedWirelessGenerationBatch<>();
        HostedNaquadahCycle cycle = paid(BigInteger.ONE);
        batch.add(cycle, cycle.nextOutput(0));
        assertTrue(batch.flush(amount -> { fail("No zero-EU ledger update needed"); return false; },
            HostedNaquadahCycle::accept, unused -> {}));
        assertEquals(1199, cycle.wirelessTicks);
        assertEquals(BigInteger.ONE, cycle.energy);
    }

    @Test
    void anInterruptedUnpaidTickCanDiscardOnlyItsTemporaryRequests() {
        HostedWirelessGenerationBatch<HostedNaquadahCycle> batch = new HostedWirelessGenerationBatch<>();
        HostedNaquadahCycle cycle = paid(BigInteger.valueOf(1200));
        batch.add(cycle, cycle.nextOutput(0));
        batch.clear();
        assertEquals(BigInteger.valueOf(1200), cycle.energy);
        assertEquals(1200, cycle.wirelessTicks);
        assertFalse(batch.flush(amount -> { fail("Unpaid request survived"); return true; },
            HostedNaquadahCycle::accept, unused -> {}));
        assertThrows(IllegalArgumentException.class, () -> batch.add(cycle, BigInteger.valueOf(-1)));
    }
}

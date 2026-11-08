package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class HostedNaquadahCycleTest {
    private HostedNaquadahCycle paid(int baseTicks, int boostedTicks, long nativeOutput) {
        HostedNaquadahCycle cycle = new HostedNaquadahCycle(baseTicks, boostedTicks, 0);
        int charges = 0;
        while (!cycle.ready()) { cycle.settle(nativeOutput); charges++; }
        assertEquals((baseTicks + 19) / 20, charges);
        assertEquals(BigInteger.valueOf(nativeOutput).multiply(BigInteger.valueOf(boostedTicks)), cycle.energy);
        return cycle;
    }

    @Test
    void screenshotBatchBecomesSixtySecondsWithExactlyTheSameEnergy() {
        HostedNaquadahCycle cycle = paid(240, 368640, 665000000000L);
        BigInteger total = cycle.energy;
        cycle.configure(true);
        BigInteger delivered = BigInteger.ZERO;
        for (int i = 0; i < 1200; i++) {
            BigInteger amount = cycle.nextOutput(1);
            assertEquals(BigInteger.valueOf(204288000000000L), amount);
            delivered = delivered.add(amount);
            cycle.accept(amount);
        }
        assertEquals(total, delivered);
        assertTrue(cycle.finished());
    }

    @Test
    void wiredOutputUsesFullCapacityAndOnlyTheRemainderOnTheLastTick() {
        HostedNaquadahCycle cycle = paid(21, 101, 13);
        cycle.configure(false);
        assertEquals(14, cycle.remainingTicks(100));
        for (int i = 0; i < 13; i++) {
            assertEquals(BigInteger.valueOf(100), cycle.nextOutput(100));
            cycle.accept(cycle.nextOutput(100));
        }
        assertEquals(BigInteger.valueOf(13), cycle.nextOutput(100));
        cycle.accept(cycle.nextOutput(100));
        assertTrue(cycle.finished());
    }

    @Test
    void integerRoundingNeverCreatesOrLosesEnergy() {
        HostedNaquadahCycle cycle = paid(41, 157, 127);
        BigInteger total = cycle.energy;
        cycle.configure(true);
        BigInteger delivered = BigInteger.ZERO;
        for (int i = 0; i < 1200; i++) {
            BigInteger amount = cycle.nextOutput(0);
            delivered = delivered.add(amount);
            cycle.accept(amount);
        }
        assertEquals(total, delivered);
        assertTrue(cycle.finished());
    }

    @Test
    void verySmallBatchStillUsesExactlySixtySeconds() {
        HostedNaquadahCycle cycle = paid(1, 1, 1);
        cycle.configure(true);
        for (int i = 0; i < 1199; i++) {
            assertEquals(BigInteger.ZERO, cycle.nextOutput(0));
            cycle.accept(BigInteger.ZERO);
            assertFalse(cycle.finished());
        }
        assertEquals(BigInteger.ONE, cycle.nextOutput(0));
        cycle.accept(BigInteger.ONE);
        assertTrue(cycle.finished());
    }

    @Test
    void aBlockedOutputDoesNotSpendEnergyOrAdvanceWirelessTime() {
        HostedNaquadahCycle cycle = paid(100, 1000, 80);
        cycle.configure(true);
        BigInteger expected = cycle.nextOutput(0);
        for (int i = 0; i < 20; i++) assertEquals(expected, cycle.nextOutput(0));
        assertEquals(1200, cycle.wirelessTicks);
        assertEquals(BigInteger.valueOf(80000), cycle.energy);
    }

    @Test
    void preparingAndGeneratingSurviveReloadAndModeChange() {
        HostedNaquadahCycle cycle = new HostedNaquadahCycle(60, 600, 0);
        cycle.settle(100);
        cycle = HostedNaquadahCycle.read(cycle.write());
        assertEquals(1, cycle.auxiliaryIndex());
        cycle.settle(200);
        cycle.settle(300);
        assertEquals(BigInteger.valueOf(120000), cycle.energy);
        cycle.configure(true);
        cycle.accept(cycle.nextOutput(0));
        cycle = HostedNaquadahCycle.read(cycle.write());
        assertEquals(1199, cycle.wirelessTicks);
        cycle.configure(false);
        BigInteger rest = cycle.energy;
        BigInteger delivered = BigInteger.ZERO;
        while (!cycle.finished()) {
            BigInteger amount = cycle.nextOutput(10000);
            delivered = delivered.add(amount);
            cycle.accept(amount);
        }
        assertEquals(rest, delivered);
    }

    @Test
    void activeOldBatchKeepsOnlyItsUnspentPortion() {
        HostedNaquadahCycle cycle = new HostedNaquadahCycle(100, 1000, 550);
        assertEquals(2, cycle.auxiliaryIndex());
        while (!cycle.ready()) cycle.settle(100);
        assertEquals(BigInteger.valueOf(45000), cycle.energy);
    }

    @Test
    void wirelessDoesNotTruncateEnergyBeyondLongRange() {
        HostedNaquadahCycle cycle = paid(100, Integer.MAX_VALUE, Long.MAX_VALUE);
        cycle.configure(true);
        assertTrue(cycle.nextOutput(1).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        BigInteger total = cycle.energy;
        BigInteger delivered = BigInteger.ZERO;
        for (int i = 0; i < 1200; i++) {
            BigInteger amount = cycle.nextOutput(1);
            delivered = delivered.add(amount);
            cycle.accept(amount);
        }
        assertEquals(total, delivered);
    }

    @Test
    void noFuelDoesNotCreateEnergyOrSkipAnAuxiliaryPeriod() {
        HostedNaquadahCycle cycle = new HostedNaquadahCycle(100, 1000, 0);
        cycle.settle(0);
        assertEquals(0, cycle.auxiliaryIndex());
        assertEquals(BigInteger.ZERO, cycle.energy);
        assertFalse(cycle.ready());
    }

    @Test
    void hostReleaseRetainsPrepaidGenerationUntilTheMachineIsHostedAgain() throws Exception {
        net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
        saved.setInteger("maxProgress", 1200);
        HostedMachineCoordinator.HostedJob job = HostedMachineCoordinator.HostedJob.readFromNBT(saved);
        job.naquadahCycle = paid(100, 1000, 100);
        job.naquadahCycle.configure(true);
        job.naquadahCycle.accept(job.naquadahCycle.nextOutput(0));
        HostedMachineCoordinator coordinator = new HostedMachineCoordinator(null);
        java.lang.reflect.Field pending = HostedMachineCoordinator.class.getDeclaredField("pendingJobs");
        pending.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, HostedMachineCoordinator.HostedJob> jobs =
            (java.util.Map<String, HostedMachineCoordinator.HostedJob>) pending.get(coordinator);
        jobs.put("0:10:20:30", job);
        coordinator.releaseAll();
        coordinator.writeToNBT(saved);
        net.minecraft.nbt.NBTTagList claims = saved.getTagList("mbHostClaims", 10);
        assertEquals(1, claims.tagCount());
        HostedMachineCoordinator.HostedJob restored = HostedMachineCoordinator.HostedJob.readFromNBT(claims.getCompoundTagAt(0));
        assertEquals(job.naquadahCycle.energy, restored.naquadahCycle.energy);
        assertEquals(1199, restored.naquadahCycle.wirelessTicks);
        restored.clear();
        assertNull(restored.naquadahCycle);
    }

    @Test
    void milestoneStatisticsKeepWirelessAmountsBeyondLongRange() {
        HostedProgression progression = new HostedProgression();
        BigInteger amount = BigInteger.TEN.pow(30);
        progression.recordGeneratorTick(amount, 20);
        assertEquals(amount, progression.getEnergyThroughput());
        assertEquals(1, progression.getWorkUnits());
        assertEquals(1, progression.getProductiveTicks());
    }

    @Test
    void cachedRateMatchesOriginalDivisionAtEveryTickIncludingReloadsAndPartialCredits() {
        java.util.Random random = new java.util.Random(19730825L);
        for (int scenario = 0; scenario < 32; scenario++) {
            HostedNaquadahCycle cycle = new HostedNaquadahCycle(1, 1, 1);
            cycle.energy = new BigInteger(160, random).add(BigInteger.ONE);
            cycle.configure(true);
            for (int tick = 0; tick < 1200; tick++) {
                BigInteger expected = cycle.energy.divide(BigInteger.valueOf(Math.max(1, cycle.wirelessTicks)));
                BigInteger output = cycle.nextOutput(0);
                assertEquals(expected, output, "Scenario " + scenario + " tick " + tick);
                assertSame(output, cycle.nextOutput(0), "A blocked/repeated request should reuse its calculated amount");
                // Exercise the documented partial-credit fallback as well as normal full credits.
                cycle.accept(tick == 40 ? output.divide(BigInteger.valueOf(2)) : output);
                if (tick == 500) cycle = HostedNaquadahCycle.read(cycle.write());
            }
            assertTrue(cycle.finished());
        }
    }

    @Test
    void zeroRemainingTicksWithUnspentEnergyNeverAddsOneEu() {
        HostedNaquadahCycle cycle = new HostedNaquadahCycle(1, 1, 1);
        cycle.energy = BigInteger.valueOf(713);
        cycle.configure(true);
        cycle.wirelessTicks = 0;
        assertEquals(BigInteger.valueOf(713), cycle.nextOutput(0));
        cycle.accept(cycle.nextOutput(0));
        assertTrue(cycle.finished());
    }
}

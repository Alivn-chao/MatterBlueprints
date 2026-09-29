package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import com.gtnhblueprints.HostedMilestoneConfig;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class HostedProgressionTest {

    @Test
    void runtimeGraduatesAtOneWeekOfProductiveTicks() {
        HostedProgression progression = from(12_096_000L, 0L, BigInteger.ZERO);
        assertEquals(6, progression.getRuntimeLevel());
        assertEquals(3, progression.getSpeedTier());
        assertEquals(25, progression.applyConsumerDuration(100));
        assertEquals(100, progression.applyConsumerPower(100));
        assertEquals(-1L, progression.getNextRuntimeTarget());
    }

    @Test
    void finalParallelIsSingleMachineCapacityTimesMachineCountSquared() {
        HostedProgression progression = from(0L, 10_000_000L, BigInteger.ZERO);
        assertEquals(3, progression.getParallelTier());
        assertEquals(9000, progression.applyParallelBonus(300, 30));
    }

    @Test
    void taskLanesFollowFourSixteenAndHostedCount() {
        assertEquals(1, from(0L, 0L, BigInteger.ZERO).getMaximumTasks(30));
        assertEquals(4, from(12_000L, 1_000L, BigInteger.ZERO).getMaximumTasks(30));
        assertEquals(16, from(432_000L, 100_000L, BigInteger.ZERO).getMaximumTasks(30));
        assertEquals(30, from(12_096_000L, 10_000_000L, BigInteger.ZERO).getMaximumTasks(30));
    }

    @Test
    void generatorBonusesBecomeLongerSingleRun() {
        HostedProgression progression = from(12_096_000L, 10_000_000L, BigInteger.TEN.pow(21));
        assertEquals(384000, progression.applyGeneratorDuration(100, 30));
        assertTrue(progression.hasVoltageExtension());
        assertEquals(4, progression.getYieldQuarterBonus());
        assertNull(progression.getNextEnergyTarget());
    }

    @Test
    void generatorMilestonesCountAcceptedEnergyAndMachineSeconds() {
        HostedProgression progression = new HostedProgression();
        for (int tick = 0; tick < 20; tick++) progression.recordGeneratorTick(5, 30);
        assertEquals(20L, progression.getProductiveTicks());
        assertEquals(30L, progression.getWorkUnits());
        assertEquals(BigInteger.valueOf(100), progression.getEnergyThroughput());
    }

    @Test
    void voltageExtensionRequiresFifthEnergyMilestone() {
        assertFalse(from(0, 0, BigInteger.TEN.pow(19).subtract(BigInteger.ONE)).hasVoltageExtension());
        assertTrue(from(0, 0, BigInteger.TEN.pow(19)).hasVoltageExtension());
    }

    @Test
    void energyMilestonesReducePerTickPowerToOneQuarter() {
        assertEquals(75L, from(0, 0, BigInteger.TEN.pow(11)).applyConsumerPower(100));
        assertEquals(50L, from(0, 0, BigInteger.TEN.pow(15)).applyConsumerPower(100));
        assertEquals(25L, from(0, 0, BigInteger.TEN.pow(21)).applyConsumerPower(100));
    }

    @Test
    void admittedPowerIncludesDiscountAndMaintenanceWithoutExceedingSupply() {
        for (int exponent : new int[] { 0, 11, 15, 21 }) {
            HostedProgression progression = from(0, 0, BigInteger.TEN.pow(exponent));
            for (int efficiency : new int[] { 1000, 4000, 9000, 10000 }) {
                for (long supply : new long[] { 1, 3, 127, 2048, 3145728 }) {
                    long nativeBudget = progression.getNativePowerBudget(supply, efficiency);
                    long actualUsage = nativeBudget * 10000L / efficiency;
                    assertTrue(progression.applyConsumerPower(actualUsage) <= supply);
                }
            }
        }
    }

    @Test
    void discountIncreasesAdmissionBudgetAndHandlesLargePower() {
        HostedProgression base = from(0, 0, BigInteger.ZERO);
        HostedProgression upgraded = from(0, 0, BigInteger.TEN.pow(21));
        assertEquals(2048, base.getNativePowerBudget(2048, 10000));
        assertEquals(8192, upgraded.getNativePowerBudget(2048, 10000));
        assertEquals(4096, upgraded.getNativePowerBudget(2048, 5000));
        assertEquals(0, upgraded.getNativePowerBudget(0, 10000));
        assertEquals(Long.MAX_VALUE, upgraded.getNativePowerBudget(Long.MAX_VALUE - 1, 10000));
        assertEquals(Long.MAX_VALUE, upgraded.getNativePowerBudget(Long.MAX_VALUE, 1000));
    }

    @Test
    void plasmaPricingUsesTheSameHostClockAsTheDisplayedUnlockProgress() {
        HostedProgression progression = from(37840L, 0, BigInteger.ZERO);
        assertFalse(progression.hasPermanentPlasmaFuelDiscount());
        assertEquals(37840L, progression.getPlasmaForgeRuntime());
        assertEquals(3.284722222222222D,
            100D * (1D - HostedPlasmaForgeSupport.fuelMultiplier(progression.getPlasmaForgeRuntime())), 1e-10);
        int previous = HostedMilestoneConfig.plasmaFuelHours;
        try {
            HostedMilestoneConfig.plasmaFuelHours = 2;
            assertEquals(288000L, from(72000L, 0, BigInteger.ZERO).getPlasmaForgeRuntime());
            assertEquals(0.75D, HostedPlasmaForgeSupport.fuelMultiplier(
                from(72000L, 0, BigInteger.ZERO).getPlasmaForgeRuntime()));
        } finally {
            HostedMilestoneConfig.plasmaFuelHours = previous;
        }
    }

    @Test
    void plasmaUnlocksAtEightHoursWithoutChangingTimeMilestones() {
        HostedProgression progression = from(575999L, 0, BigInteger.ZERO);
        assertFalse(progression.hasPermanentPlasmaFuelDiscount());
        progression.recordConsumerTick(100, false);
        assertEquals(575999L, progression.getProductiveTicks());
        assertFalse(progression.hasPermanentPlasmaFuelDiscount());
        progression.recordConsumerTick(100, true);
        assertEquals(3, progression.getRuntimeLevel());
        assertTrue(progression.hasPermanentPlasmaFuelDiscount());
        assertEquals(BigInteger.valueOf(200), progression.getEnergyThroughput());
        assertTrue(from(576000L, 0, BigInteger.ZERO).hasPermanentPlasmaFuelDiscount());
    }

    @Test
    void plasmaFuelUnlockSurvivesReloadAndRaisedThresholds() {
        HostedProgression progression = from(12_096_000L, 0, BigInteger.ZERO);
        assertTrue(progression.hasPermanentPlasmaFuelDiscount());
        NBTTagCompound saved = new NBTTagCompound();
        progression.writeToNBT(saved);
        long[] original = HostedMilestoneConfig.runtimeTicks;
        int hours = HostedMilestoneConfig.plasmaFuelHours;
        try {
            HostedMilestoneConfig.plasmaFuelHours = 1000;
            HostedMilestoneConfig.runtimeTicks = new long[] { 20_000_000, 30_000_000, 40_000_000,
                50_000_000, 60_000_000, 70_000_000 };
            HostedProgression restored = new HostedProgression();
            restored.readFromNBT(saved);
            assertEquals(0, restored.getRuntimeLevel());
            assertTrue(restored.hasPermanentPlasmaFuelDiscount());
            assertFalse(new HostedProgression().hasPermanentPlasmaFuelDiscount());
        } finally {
            HostedMilestoneConfig.runtimeTicks = original;
            HostedMilestoneConfig.plasmaFuelHours = hours;
        }
    }

    @Test
    void configurableEffectsKeepPowerBudgetConsistent() {
        int[] power = HostedMilestoneConfig.powerPercent;
        int[] duration = HostedMilestoneConfig.durationPercent;
        try {
            HostedMilestoneConfig.powerPercent = new int[] { 80, 60, 33 };
            HostedMilestoneConfig.durationPercent = new int[] { 90, 70, 40 };
            HostedProgression progression = from(12_096_000, 0, BigInteger.TEN.pow(21));
            assertEquals(40, progression.applyConsumerDuration(100));
            assertEquals(33, progression.applyConsumerPower(100));
            long budget = progression.getNativePowerBudget(100, 9000);
            assertTrue(progression.applyConsumerPower(budget * 10000 / 9000) <= 100);
            assertEquals("0.40", progression.getDurationMultiplier());
            assertEquals("0.33", progression.getPowerMultiplier());
        } finally {
            HostedMilestoneConfig.powerPercent = power;
            HostedMilestoneConfig.durationPercent = duration;
        }
    }

    @Test
    void plasmaUnlockOccursAtConfiguredHours() {
        int original = HostedMilestoneConfig.plasmaFuelHours;
        try {
            HostedMilestoneConfig.plasmaFuelHours = 1;
            HostedProgression progression = from(71999, 0, BigInteger.ZERO);
            assertFalse(progression.hasPermanentPlasmaFuelDiscount());
            progression.recordConsumerTick(1);
            assertTrue(progression.hasPermanentPlasmaFuelDiscount());
        } finally {
            HostedMilestoneConfig.plasmaFuelHours = original;
        }
    }

    private static HostedProgression from(long ticks, long work, BigInteger energy) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("productiveTicks", ticks);
        tag.setLong("workUnits", work);
        tag.setByteArray("energyThroughput", energy.toByteArray());
        HostedProgression progression = new HostedProgression();
        progression.readFromNBT(tag);
        return progression;
    }
}

package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.gtnhblueprints.HostedMilestoneConfig;
import gregtech.api.util.OverclockCalculator;

class HostedAssemblyOverclockTest {

    @Test
    void ordinaryAssemblyAcceptsUmvRecipeAtTheNextHatchTierButAdvancedDoesNot() {
        long effectiveHatchVoltage = 33_554_432L;
        assertTrue(HostedAssemblySupport.acceptsRecipeVoltage(125_829_120, effectiveHatchVoltage, false));
        assertFalse(HostedAssemblySupport.acceptsRecipeVoltage(125_829_120, effectiveHatchVoltage, true));
        assertFalse(HostedAssemblySupport.acceptsRecipeVoltage(125_829_120, 8_388_608L, false));
        assertFalse(HostedAssemblySupport.acceptsRecipeVoltage(0, effectiveHatchVoltage, false));
        assertFalse(HostedAssemblySupport.acceptsRecipeVoltage(32, 0, false));
    }

    @Test
    void mixedVoltageExecutorsKeepIndependentDurationAndConsumption() {
        OverclockCalculator low = HostedAssemblySupport.calculator(32, 1024, 128, 1, 1).calculate();
        OverclockCalculator high = HostedAssemblySupport.calculator(32, 1024, 2048, 1, 1).calculate();
        assertTrue(low.getConsumption() <= 128);
        assertTrue(high.getConsumption() <= 2048);
        assertTrue(high.getConsumption() > low.getConsumption());
        assertTrue(high.getDuration() < low.getDuration());
    }

    @Test
    void defaultEquivalentParallelCountsInputEntriesNotStackAmounts() {
        assertEquals(1, HostedAssemblySupport.baseParallel(false, 6));
        assertEquals(6, HostedAssemblySupport.baseParallel(true, 6));
        int previous = HostedMilestoneConfig.advancedAssemblyFixedParallel;
        try {
            HostedMilestoneConfig.advancedAssemblyFixedParallel = 16;
            assertEquals(16, HostedAssemblySupport.baseParallel(true, 6));
            assertEquals(1, HostedAssemblySupport.baseParallel(false, 6));
        } finally {
            HostedMilestoneConfig.advancedAssemblyFixedParallel = previous;
        }
    }

    @Test
    void energyLevelsSelectLaserRegularThenPerfect() {
        assertEquals(0, HostedAssemblySupport.overclockMode(true, 0));
        assertEquals(0, HostedAssemblySupport.overclockMode(true, 2));
        assertEquals(1, HostedAssemblySupport.overclockMode(true, 3));
        assertEquals(2, HostedAssemblySupport.overclockMode(true, 6));
        assertEquals(1, HostedAssemblySupport.overclockMode(false, 6));
    }

    @Test
    void perfectOverclockUsesSamePowerForTwiceTheRegularSpeed() {
        OverclockCalculator regular = HostedAssemblySupport.calculator(32, 1024, 128, 1, 1).calculate();
        OverclockCalculator perfect = HostedAssemblySupport.calculator(32, 1024, 128, 1, 2).calculate();
        assertEquals(regular.getConsumption(), perfect.getConsumption());
        assertEquals(regular.getDuration() / 2, perfect.getDuration());
    }

    @Test
    void upgradedRegularOverclockRetainsAmperageAndRemovesLaserPenalty() {
        OverclockCalculator laser = HostedAssemblySupport.calculator(32, 1024, 128, 64, 0).calculate();
        OverclockCalculator regular = HostedAssemblySupport.calculator(32, 1024, 128, 64, 1).calculate();
        assertTrue(laser.getConsumption() <= 128 * 64);
        assertTrue(regular.getConsumption() <= 128 * 64);
        assertTrue(regular.getDuration() <= laser.getDuration(), "regular=" + regular.getDuration() + "/"
            + regular.getConsumption() + " laser=" + laser.getDuration() + "/" + laser.getConsumption());
        OverclockCalculator sameOverclocks = HostedAssemblySupport.calculator(32, 1024, 128, 64, 1)
            .setMaxOverclocks(laser.getPerformedOverclocks()).calculate();
        assertTrue(sameOverclocks.getConsumption() <= laser.getConsumption());
    }

    @Test
    void laserModeUsesHighAmperageEvenWhenRecipeAlreadyMatchesUmvVoltage() {
        long voltage = 134_217_728L;
        int recipeEUt = 125_829_120;
        OverclockCalculator singleAmp = HostedAssemblySupport.calculator(recipeEUt, 480_000, voltage, 1, 0)
            .setDurationModifier(0.75).calculate();
        OverclockCalculator highAmp = HostedAssemblySupport.calculator(recipeEUt, 480_000, voltage, 65_536, 0)
            .setDurationModifier(0.75).calculate();
        assertEquals(0, singleAmp.getPerformedOverclocks());
        assertEquals(360_000, singleAmp.getDuration());
        assertTrue(highAmp.getPerformedOverclocks() > 0, "65536A must enable laser overclocking at the same voltage tier");
        assertTrue(highAmp.getDuration() < singleAmp.getDuration());
        assertTrue(highAmp.getConsumption() > recipeEUt);
        assertTrue(highAmp.getConsumption() <= voltage * 65_536);
        OverclockCalculator regular = HostedAssemblySupport.calculator(recipeEUt, 480_000, voltage, 65_536, 1)
            .setDurationModifier(0.75).calculate();
        assertTrue(regular.getDuration() <= highAmp.getDuration());
    }

    @Test
    void laserModeKeepsItsIncreasingEnergyPenalty() {
        OverclockCalculator laser = HostedAssemblySupport.calculator(32, 1024, 32, 64, 0).calculate();
        assertTrue(laser.getPerformedOverclocks() >= 2);
        int ocs = laser.getPerformedOverclocks();
        OverclockCalculator regular = HostedAssemblySupport.calculator(32, 1024, 32, 64, 1)
            .setMaxOverclocks(ocs).calculate();
        assertEquals(regular.getDuration(), laser.getDuration());
        assertTrue(laser.getConsumption() > regular.getConsumption());
        assertTrue(laser.getConsumption() <= 32 * 64);
    }
}

package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

class HostedMilestoneCreditTest {
    @Test
    void halfTimeHalfPowerCreditsOneOriginalSecondAndItsFullEnergy() {
        HostedProgression progression = new HostedProgression();
        HostedMilestoneCredit job = new HostedMilestoneCredit(50, 100, 50);
        long actuallyPaid = 0;
        for (int i = 0; i < 10; i++) {
            actuallyPaid += 50;
            progression.recordConsumerCredit(job.durationPercent, job.paid(50), true);
        }
        assertEquals(500, actuallyPaid);
        assertEquals(20, progression.getProductiveTicks());
        assertEquals(BigInteger.valueOf(2000), progression.getEnergyThroughput());
    }

    @Test
    void fractionalTimeAndEnergySurviveSaveAndPortableMilestones() {
        HostedProgression progression = new HostedProgression();
        HostedMilestoneCredit job = new HostedMilestoneCredit(75, 1, 1);
        for (int i = 0; i < 15; i++) {
            progression.recordConsumerCredit(job.durationPercent, job.paid(1), true);
            NBTTagCompound saved = new NBTTagCompound(); progression.writeToNBT(saved);
            progression = new HostedProgression(); progression.readFromNBT(saved);
            job = HostedMilestoneCredit.read(job.write());
        }
        assertEquals(20, progression.getProductiveTicks());
        assertEquals(BigInteger.valueOf(20), progression.getEnergyThroughput());
    }

    @Test
    void everySupportedCustomPercentageHasExactFractions() {
        for (int percent = 1; percent <= 100; percent++) {
            HostedProgression progression = new HostedProgression();
            HostedMilestoneCredit job = new HostedMilestoneCredit(percent, 31, 11);
            for (int i = 0; i < percent; i++) progression.recordConsumerCredit(percent, job.paid(11), true);
            assertEquals(100, progression.getProductiveTicks(), "percent=" + percent);
            assertEquals(BigInteger.valueOf(3100), progression.getEnergyThroughput(), "percent=" + percent);
        }
    }

    @Test
    void partialWiredSupplyDoesNotCreditUnpaidEnergyOrAdvanceTime() {
        HostedProgression progression = new HostedProgression();
        HostedMilestoneCredit job = new HostedMilestoneCredit(75, 10, 7);
        progression.recordConsumerCredit(100, job.paid(2), false);
        assertEquals(0, progression.getProductiveTicks());
        assertEquals(BigInteger.valueOf(3), progression.getEnergyThroughput());
        job = HostedMilestoneCredit.read(job.write());
        progression.recordConsumerCredit(100, job.paid(3), false);
        progression.recordConsumerCredit(75, job.paid(2), true);
        for (int i = 0; i < 2; i++) progression.recordConsumerCredit(75, job.paid(7), true);
        assertEquals(4, progression.getProductiveTicks());
        assertEquals(BigInteger.valueOf(40), progression.getEnergyThroughput());
        progression.recordConsumerCredit(75, job.paid(0), false);
        assertEquals(4, progression.getProductiveTicks());
        assertEquals(BigInteger.valueOf(40), progression.getEnergyThroughput());
    }

    @Test
    void parallelJobsSumEnergyButOnlyAdvanceOneHostClock() {
        HostedProgression progression = new HostedProgression();
        HostedMilestoneCredit[] jobs = new HostedMilestoneCredit[128];
        for (int i = 0; i < jobs.length; i++) jobs[i] = new HostedMilestoneCredit(50, Long.MAX_VALUE, Long.MAX_VALUE / 2 + 1);
        for (int tick = 0; tick < 10; tick++) {
            BigInteger energy = BigInteger.ZERO;
            for (HostedMilestoneCredit job : jobs) energy = energy.add(job.paid(Long.MAX_VALUE / 2 + 1));
            progression.recordConsumerCredit(50, energy, true);
        }
        assertEquals(20, progression.getProductiveTicks());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(128 * 20)), progression.getEnergyThroughput());
    }

    @Test
    void jobPricingSurvivesPersistenceAndDoesNotChangeWithLaterMilestones() {
        HostedMachineCoordinator.HostedJob job = new HostedMachineCoordinator.HostedJob();
        NBTTagCompound saved = new NBTTagCompound(); job.writeToNBT(saved);
        saved.setInteger("maxProgress", 20);
        saved.setTag("milestoneCredit", new HostedMilestoneCredit(75, 100, 75).write());
        job = HostedMachineCoordinator.HostedJob.readFromNBT(saved);
        NBTTagCompound restored = new NBTTagCompound(); job.writeToNBT(restored);
        assertEquals(saved.getCompoundTag("milestoneCredit"), restored.getCompoundTag("milestoneCredit"));
        job.clear(); job.writeToNBT(restored);
        assertFalse(restored.hasKey("milestoneCredit"));
    }

    @Test
    void legacyCountersAreNotRetroactivelyMultipliedAndGenerationKeepsActualEnergy() {
        NBTTagCompound old = new NBTTagCompound();
        old.setLong("productiveTicks", 500);
        old.setByteArray("energyThroughput", BigInteger.valueOf(800).toByteArray());
        HostedProgression progression = new HostedProgression(); progression.readFromNBT(old);
        assertEquals(500, progression.getProductiveTicks());
        assertEquals(BigInteger.valueOf(800), progression.getEnergyThroughput());
        progression.recordGeneratorTick(20, 128);
        progression.recordStartupEnergy(30);
        assertEquals(501, progression.getProductiveTicks());
        assertEquals(BigInteger.valueOf(850), progression.getEnergyThroughput());
    }
}

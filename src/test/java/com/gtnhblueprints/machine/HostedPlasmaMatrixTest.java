package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import gregtech.api.util.OverclockCalculator;

class HostedPlasmaMatrixTest {
    @BeforeAll
    static void initialize() throws Exception { AssemblyTestBootstrap.initialize(); }

    private HostedPlasmaForgeSupport.CohortKey key(boolean matrix) {
        return new HostedPlasmaForgeSupport.CohortKey(matrix, 10000, 1, false, 32768, 131072);
    }

    private HostedMachineCoordinator.HostedJob active(String... members) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("maxProgress", 100);
        HostedMachineCoordinator.HostedJob job = HostedMachineCoordinator.HostedJob.readFromNBT(tag);
        job.plasmaMembers.addAll(Arrays.asList(members));
        return job;
    }

    @Test
    void oneMatrixAmongTenForgesNeverUpgradesTheOtherNine() {
        Map<HostedPlasmaForgeSupport.CohortKey, Integer> groups = new HashMap<>();
        groups.merge(key(true), 1, Integer::sum);
        for (int i = 0; i < 9; i++) groups.merge(key(false), 1, Integer::sum);
        assertEquals(2, groups.size());
        assertEquals(1, groups.get(key(true)).intValue());
        assertEquals(9, groups.get(key(false)).intValue());
        HostedProgression growth = new HostedProgression();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("workUnits", 10000000L);
        growth.readFromNBT(tag);
        assertEquals(10, growth.applyParallelBonus(groups.get(key(true)), 10));
        assertEquals(90, growth.applyParallelBonus(groups.get(key(false)), 10));
    }

    @Test
    void heatCatalystVoltageAndAmperageRemainSeparate() {
        assertNotEquals(key(true), new HostedPlasmaForgeSupport.CohortKey(true, 12000, 1, false, 32768, 131072));
        assertNotEquals(key(true), new HostedPlasmaForgeSupport.CohortKey(true, 10000, 2, false, 32768, 131072));
        assertNotEquals(key(true), new HostedPlasmaForgeSupport.CohortKey(true, 10000, 1, false, 131072, 131072));
        assertNotEquals(key(true), new HostedPlasmaForgeSupport.CohortKey(true, 10000, 1, false, 32768, 32768));
    }

    @Test
    void detectsTheExactMatrixWithoutConsumingIt() {
        ItemStack matrix = new ItemStack(Items.feather, 1, 32758);
        ItemStack installed = matrix.copy();
        assertTrue(HostedPlasmaForgeSupport.isMatrix(installed, matrix));
        assertFalse(HostedPlasmaForgeSupport.isMatrix(new ItemStack(Items.feather, 1, 32757), matrix));
        assertFalse(HostedPlasmaForgeSupport.isMatrix(new ItemStack(Items.diamond, 1, 32758), matrix));
        assertFalse(HostedPlasmaForgeSupport.isMatrix(null, matrix));
        assertFalse(HostedPlasmaForgeSupport.isMatrix(new ItemStack(Items.feather, 0, 32758), matrix));
        assertEquals(1, installed.stackSize);
    }

    @Test
    void membershipSurvivesSaveAndPreventsMovingAMatrixFromDoubleBookingMachines() {
        HostedMachineCoordinator.HostedJob job = active("0:1:2:3", "0:2:2:3");
        job.plasmaMatrix = true;
        NBTTagCompound saved = new NBTTagCompound();
        job.writeToNBT(saved);
        HostedMachineCoordinator.HostedJob restored = HostedMachineCoordinator.HostedJob.readFromNBT(saved);
        assertTrue(restored.plasmaMatrix);
        HostedPlasmaForgeSupport.Reservations reservations = HostedPlasmaForgeSupport.reservations(Arrays.asList(restored));
        assertTrue(reservations.contains("0:1:2:3"));
        assertTrue(reservations.contains("0:2:2:3"));
        assertFalse(reservations.contains("0:3:2:3"));
        restored.clear();
        assertFalse(restored.plasmaMatrix);
        assertTrue(restored.plasmaMembers.isEmpty());
        assertFalse(HostedPlasmaForgeSupport.reservations(Arrays.asList(restored)).contains("0:1:2:3"));
    }

    @Test
    void activeLegacyJobsReserveTheOldWholeBankUntilCompletion() {
        HostedMachineCoordinator.HostedJob job = active();
        assertTrue(HostedPlasmaForgeSupport.reservations(Arrays.asList(job)).contains("any-forge"));
        job.clear();
        assertFalse(HostedPlasmaForgeSupport.reservations(Arrays.asList(job)).contains("any-forge"));
    }

    @Test
    void perfectOcAtEqualVoltageUsesFourfoldTimeReductionWithoutExtraPower() {
        OverclockCalculator ordinary = new OverclockCalculator().setRecipeEUt(32).setEUt(512).setDuration(1024).calculate();
        OverclockCalculator matrix = new OverclockCalculator().setRecipeEUt(32).setEUt(512).setDuration(1024)
            .setDurationDecreasePerOC(4).setEUtIncreasePerOC(4).calculate();
        assertEquals(ordinary.getConsumption(), matrix.getConsumption());
        assertEquals(256, ordinary.getDuration());
        assertEquals(64, matrix.getDuration());
    }
}

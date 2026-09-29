package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class HostedJobPersistenceTest {

    @Test
    void roundTripsActiveProgress() {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("progress", 73);
        saved.setInteger("maxProgress", 240);
        saved.setInteger("machineCount", 12);
        saved.setInteger("parallels", 768);
        saved.setInteger("reservedParallel", 6);
        saved.setString("recipeKey", "test-recipe-key");
        saved.setLong("energyUsage", 3145728L);
        saved.setLong("energyCredit", 1048576L);
        saved.setLong("plasmaRuntime", 250000L);
        saved.setFloat("thermalGrowth", 1.75F);
        saved.setInteger("thermalTicks", 87);
        saved.setInteger("thermalBooster", 2);
        saved.setBoolean("equivalentAssembly", true);
        saved.setInteger("assemblyUnits", 2);
        saved.setInteger("recipeEUt", -2048);
        saved.setLong("recipeLongEUt", -3145728L);
        saved.setLong("nextRecipeCheckTick", 9001L);
        saved.setInteger("idleRecipeCheckDelay", 80);

        HostedMachineCoordinator.HostedJob restored =
            HostedMachineCoordinator.HostedJob.readFromNBT(saved);
        NBTTagCompound roundTrip = new NBTTagCompound();
        restored.writeToNBT(roundTrip);

        assertTrue(restored.isActive());
        assertEquals(73, roundTrip.getInteger("progress"));
        assertEquals(240, roundTrip.getInteger("maxProgress"));
        assertEquals(12, roundTrip.getInteger("machineCount"));
        assertEquals(768, roundTrip.getInteger("parallels"));
        assertEquals(6, roundTrip.getInteger("reservedParallel"));
        assertEquals("test-recipe-key", roundTrip.getString("recipeKey"));
        assertEquals(3145728L, roundTrip.getLong("energyUsage"));
        assertEquals(1048576L, roundTrip.getLong("energyCredit"));
        assertEquals(250000L, roundTrip.getLong("plasmaRuntime"));
        assertEquals(1.75F, roundTrip.getFloat("thermalGrowth"));
        assertEquals(87, roundTrip.getInteger("thermalTicks"));
        assertEquals(2, roundTrip.getInteger("thermalBooster"));
        assertTrue(roundTrip.getBoolean("equivalentAssembly"));
        assertEquals(2, roundTrip.getInteger("assemblyUnits"));
        assertEquals(-2048, roundTrip.getInteger("recipeEUt"));
        assertEquals(-3145728L, roundTrip.getLong("recipeLongEUt"));
        assertEquals(9001L, roundTrip.getLong("nextRecipeCheckTick"));
        assertEquals(80, roundTrip.getInteger("idleRecipeCheckDelay"));
    }

    @Test
    void clampsInvalidNegativeProgressAndRemainsIdle() {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("progress", -10);
        saved.setInteger("maxProgress", -20);

        HostedMachineCoordinator.HostedJob restored =
            HostedMachineCoordinator.HostedJob.readFromNBT(saved);
        NBTTagCompound roundTrip = new NBTTagCompound();
        restored.writeToNBT(roundTrip);

        assertFalse(restored.isActive());
        assertEquals(-1L, restored.plasmaRuntime);
        assertEquals(-1F, restored.thermalGrowth);
        assertEquals(-2, restored.thermalBooster);
        assertEquals(0, roundTrip.getInteger("progress"));
        assertEquals(0, roundTrip.getInteger("maxProgress"));
    }

    @Test
    void legacyJobReservesItsOriginalParallelCount() {
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("parallels", 8);
        legacy.setInteger("maxProgress", 20);
        NBTTagCompound saved = new NBTTagCompound();
        HostedMachineCoordinator.HostedJob.readFromNBT(legacy).writeToNBT(saved);
        assertEquals(8, saved.getInteger("reservedParallel"));
    }
}

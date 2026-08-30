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
        saved.setLong("energyUsage", 3145728L);

        HostedMachineCoordinator.HostedJob restored =
            HostedMachineCoordinator.HostedJob.readFromNBT(saved);
        NBTTagCompound roundTrip = new NBTTagCompound();
        restored.writeToNBT(roundTrip);

        assertTrue(restored.isActive());
        assertEquals(73, roundTrip.getInteger("progress"));
        assertEquals(240, roundTrip.getInteger("maxProgress"));
        assertEquals(12, roundTrip.getInteger("machineCount"));
        assertEquals(768, roundTrip.getInteger("parallels"));
        assertEquals(3145728L, roundTrip.getLong("energyUsage"));
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
        assertEquals(0, roundTrip.getInteger("progress"));
        assertEquals(0, roundTrip.getInteger("maxProgress"));
    }
}

package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

class HostedRecipeSchedulingTest {
    @Test
    void leavesPowerForOtherRecipesInsteadOfGivingEverythingToFirst() {
        long available = 1024;
        long first = HostedRecipeScheduling.powerShare(available, 4, 32);
        long second = HostedRecipeScheduling.powerShare(available - first, 3, 32);
        long third = HostedRecipeScheduling.powerShare(available - first - second, 2, 32);
        long fourth = HostedRecipeScheduling.powerShare(available - first - second - third, 1, 32);
        assertEquals(256, first);
        assertEquals(256, second);
        assertEquals(256, third);
        assertEquals(256, fourth);
        assertEquals(available, first + second + third + fourth);
    }

    @Test
    void minimumRecipeDemandNeverExceedsRemainingSupply() {
        assertEquals(800, HostedRecipeScheduling.powerShare(1024, 4, 800));
        assertEquals(224, HostedRecipeScheduling.powerShare(224, 3, 800));
        assertEquals(0, HostedRecipeScheduling.powerShare(0, 2, 32));
        assertTrue(HostedRecipeScheduling.powerShare(Long.MAX_VALUE, 64, 32) > 0);
    }

    @Test
    void separateTasksOnSameMachinePreserveIndependentProgressAndReservations() {
        NBTTagCompound first = savedJob(20, 100, 32, "recipe-a");
        NBTTagCompound second = savedJob(60, 200, 128, "recipe-b");
        HostedMachineCoordinator.ExtraTask a = HostedMachineCoordinator.ExtraTask.read(first);
        HostedMachineCoordinator.ExtraTask b = HostedMachineCoordinator.ExtraTask.read(second);
        assertEquals(a.machine, b.machine);
        assertEquals(20, a.write().getInteger("progress"));
        assertEquals(60, b.write().getInteger("progress"));
        assertEquals(100, a.write().getInteger("maxProgress"));
        assertEquals(200, b.write().getInteger("maxProgress"));
        assertEquals(32, a.write().getLong("energyUsage"));
        assertEquals(128, b.write().getLong("energyUsage"));
        a.job.clear();
        assertFalse(a.job.isActive());
        assertTrue(b.job.isActive());
        assertEquals("recipe-b", b.write().getString("recipeKey"));
    }

    private NBTTagCompound savedJob(int progress, int duration, long power, String recipe) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("machine", "0:100:64:100");
        tag.setInteger("progress", progress);
        tag.setInteger("maxProgress", duration);
        tag.setLong("energyUsage", power);
        tag.setInteger("parallels", 2);
        tag.setString("recipeKey", recipe);
        return tag;
    }
}

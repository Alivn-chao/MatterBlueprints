package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import gregtech.common.tileentities.machines.multi.MTELargeNaquadahReactor;

class HostedNaquadahSupportTest {
    @org.junit.jupiter.api.BeforeAll
    static void initialize() throws Exception {
        AssemblyTestBootstrap.initialize();
        java.lang.reflect.Field side = cpw.mods.fml.relauncher.FMLLaunchHandler.class.getDeclaredField("side");
        side.setAccessible(true);
        side.set(null, cpw.mods.fml.relauncher.Side.SERVER);
    }
    @Test
    void spaceAndTimeChargesDoNotIncreaseWhenRecipeIsExtended() {
        for (int length : new int[] { 100, 334, 1600, 384000 }) {
            int previous = -1;
            int charges = 0;
            for (int tick = 0; tick < length; tick++) {
                int cycle = HostedNaquadahSupport.auxiliaryCycle(tick, 100, length);
                if (cycle > previous) { charges++; previous = cycle; }
            }
            assertEquals(5, charges, "one extended run must still consume five native seconds of auxiliary fuel");
        }
    }

    @Test
    void savedAuxiliaryClockContinuesWithoutChargingTwice() {
        HostedMachineCoordinator.HostedJob job = new HostedMachineCoordinator.HostedJob();
        job.generatorBaseDuration = 100;
        job.generatorAuxiliaryCycle = 2;
        NBTTagCompound tag = new NBTTagCompound();
        job.writeToNBT(tag);
        HostedMachineCoordinator.HostedJob restored = HostedMachineCoordinator.HostedJob.readFromNBT(tag);
        assertEquals(100, restored.generatorBaseDuration);
        assertEquals(2, restored.generatorAuxiliaryCycle);
        assertEquals(2, HostedNaquadahSupport.auxiliaryCycle(8000, 100, 20000));
        restored.clear();
        assertEquals(0, restored.generatorBaseDuration);
        assertEquals(-1, restored.generatorAuxiliaryCycle);
    }

    @Test
    void oldJobsKeepTheirExistingNativeFuelCadence() {
        HostedMachineCoordinator.HostedJob job = HostedMachineCoordinator.HostedJob.readFromNBT(new NBTTagCompound());
        assertEquals(0, job.generatorBaseDuration);
        assertEquals(-1, job.generatorAuxiliaryCycle);
    }

    @Test
    void outputContinuesEachTickAndAStalledTickDoesNotRepeatTheCharge() throws Exception {
        java.lang.reflect.Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Reactor reactor;
        String threadName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("Server thread");
            reactor = (Reactor) ((sun.misc.Unsafe) singleton.get(null)).allocateInstance(Reactor.class);
        } finally { Thread.currentThread().setName(threadName); }
        reactor.mMaxProgresstime = 1000;
        HostedMachineCoordinator.HostedJob job = new HostedMachineCoordinator.HostedJob();
        job.generatorBaseDuration = 100;
        for (int tick = 0; tick < 1000; tick++) {
            reactor.mProgresstime = tick;
            assertTrue(HostedNaquadahSupport.run(reactor, job, reactor::generated));
            assertEquals(tick, reactor.mProgresstime);
            if (tick == 0) {
                NBTTagCompound saved = new NBTTagCompound();
                job.writeToNBT(saved);
                job = HostedMachineCoordinator.HostedJob.readFromNBT(saved);
                reactor.trueOutputReset();
                HostedNaquadahSupport.run(reactor, job, reactor::generated);
            }
        }
        assertEquals(5, reactor.charges);
        assertEquals(1001, reactor.outputTicks);
    }

    static final class Reactor extends MTELargeNaquadahReactor {
        int charges;
        int outputTicks;
        long generated() { return trueOutput; }
        void trueOutputReset() { trueOutput = 0; }
        Reactor() { super("hosted-naquadah-test"); }
        @Override public ItemStack getControllerSlot() { return null; }
        @Override public boolean onRunningTick(ItemStack stack) {
            if (mProgresstime % 20 == 0) { charges++; trueOutput = 100; }
            outputTicks++;
            return true;
        }
    }
}

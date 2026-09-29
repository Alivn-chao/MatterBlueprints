package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.ParallelHelper;

class HostedFusionSupportTest {
    @Test
    void twoFourAndFiveReactorsHaveProportionalPowerAtTheSameVoltage() {
        for (int machines : new int[] { 2, 4, 5 }) {
            long amps = HostedFusionSupport.aggregateAmperage(1, machines);
            gregtech.api.util.OverclockCalculator calculator = new gregtech.api.util.OverclockCalculator()
                .setRecipeEUt(8388608L).setEUt(8388608L).setAmperage(amps).setAmperageOC(false)
                .setParallel(machines).setDuration(400).calculate();
            assertEquals(8388608L * machines, calculator.getConsumption());
            assertEquals(400, calculator.getDuration());
        }
    }

    @Test
    void partialOutputKeepsRemainderAndNeverEjectsDeliveredFluidAgain() throws Exception {
        AssemblyTestBootstrap.initialize();
        FluidStack[] outputs = { new FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000) };
        AtomicLong delivered = new AtomicLong();
        assertFalse(HostedFusionSupport.flushFluids(outputs, stack -> { stack.amount -= 400; delivered.addAndGet(400); }));
        assertEquals(600, outputs[0].amount);
        assertTrue(HostedFusionSupport.flushFluids(outputs, stack -> { delivered.addAndGet(stack.amount); stack.amount = 0; }));
        assertTrue(HostedFusionSupport.flushFluids(outputs, stack -> fail("Already delivered")));
        assertEquals(1000, delivered.get());
    }

    @Test
    void waivedStartupNeverRequestsEnergy() {
        HostedFusionSupport.Ignition ignition = new HostedFusionSupport.Ignition();
        assertTrue(ignition.start(0, fee -> { fail("Hosted startup must be free"); return false; }, () -> {}));
    }
    @Test
    void chargesOnlyFirstSuccessfulStartIncludingDifferentFollowingFees() {
        HostedFusionSupport.Ignition ignition = new HostedFusionSupport.Ignition();
        AtomicLong spent = new AtomicLong();
        AtomicLong inputs = new AtomicLong();
        assertTrue(ignition.start(160000000L, fee -> { spent.addAndGet(fee); return true; }, inputs::incrementAndGet));
        assertTrue(ignition.start(320000000L, fee -> { fail("Continuous operation must not pay again"); return false; }, inputs::incrementAndGet));
        assertEquals(160000000L, spent.get());
        assertEquals(2, inputs.get());
    }

    @Test
    void insufficientIgnitionEnergyDoesNotConsumeInputsOrUnlock() {
        HostedFusionSupport.Ignition ignition = new HostedFusionSupport.Ignition();
        assertFalse(ignition.start(160000000L, fee -> false, () -> fail("Inputs consumed without ignition")));
        assertFalse(ignition.paid);
        assertTrue(ignition.start(160000000L, fee -> true, () -> {}));
        assertTrue(ignition.paid);
    }

    @Test
    void persistedIgnitionSurvivesReloadAndLegacySavesStartUnpaid() {
        HostedFusionSupport.Ignition ignition = new HostedFusionSupport.Ignition();
        ignition.read(new NBTTagCompound());
        assertFalse(ignition.paid);
        ignition.start(1, fee -> true, () -> {});
        NBTTagCompound tag = new NBTTagCompound();
        ignition.write(tag);
        HostedFusionSupport.Ignition restored = new HostedFusionSupport.Ignition();
        restored.read(tag);
        assertTrue(restored.start(100, fee -> { fail("Reload charged ignition again"); return false; }, () -> {}));
    }

    @Test
    void realParallelHelperDefersAllInputsUntilSuccessfulIgnition() throws Exception {
        AssemblyTestBootstrap.initialize();
        java.lang.reflect.Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        GTRecipe recipe = (GTRecipe) ((sun.misc.Unsafe) singleton.get(null)).allocateInstance(GTRecipe.class);
        recipe.mInputs = new ItemStack[] { new ItemStack(Items.iron_ingot, 2) };
        recipe.mFluidInputs = new FluidStack[0];
        recipe.mOutputs = new ItemStack[0];
        recipe.mFluidOutputs = new FluidStack[0];
        recipe.mInputChances = new int[] { 10000 };
        recipe.mOutputChances = new int[0];
        recipe.mFluidInputChances = new int[0];
        recipe.mFluidOutputChances = new int[0];
        recipe.mEUt = 32;
        recipe.mDuration = 100;
        recipe.mEnabled = true;
        ItemStack stock = new ItemStack(Items.iron_ingot, 10);
        List<Runnable> pending = new ArrayList<Runnable>();
        gregtech.api.interfaces.tileentity.IVoidable machine = (gregtech.api.interfaces.tileentity.IVoidable)
            java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { gregtech.api.interfaces.tileentity.IVoidable.class }, (proxy, method, args) -> {
                    if (method.getReturnType() == int.class) return Integer.MAX_VALUE;
                    if (method.getReturnType() == boolean.class) return false;
                    throw new UnsupportedOperationException(method.getName());
                });
        ParallelHelper helper = HostedFusionSupport.deferInputs(new ParallelHelper().setRecipe(recipe)
            .setMachine(machine)
            .setItemInputs(stock).setFluidInputs(new FluidStack[0]).setMaxParallel(3)
            .setAvailableEUt(96).setConsumption(true), pending, new ItemStack[] { stock }, new FluidStack[0]).build();
        assertTrue(helper.getResult().wasSuccessful());
        assertEquals(3, helper.getCurrentParallel());
        assertEquals(10, stock.stackSize);
        HostedFusionSupport.Ignition ignition = new HostedFusionSupport.Ignition();
        assertFalse(ignition.start(100, fee -> false, () -> pending.forEach(Runnable::run)));
        assertEquals(10, stock.stackSize);
        assertTrue(ignition.start(100, fee -> true, () -> pending.forEach(Runnable::run)));
        assertEquals(4, stock.stackSize);
    }

    @Test
    void largeFusionParallelMultiplicationCannotOverflow() {
        assertEquals(384, HostedFusionSupport.parallelProduct(64, 6));
        assertEquals(Integer.MAX_VALUE, HostedFusionSupport.parallelProduct(Integer.MAX_VALUE, 64));
    }
}

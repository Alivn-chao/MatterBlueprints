package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import java.util.stream.Stream;

class HostedAssemblyRecipeTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() throws Exception {
        AssemblyTestBootstrap.initialize();
    }

    @Test
    void nativeParallelHelperPlansOnCopiesThenCommitsExactlyOnce() throws Exception {
        Item input = net.minecraft.init.Items.iron_ingot;
        GTRecipe.RecipeAssemblyLine source = new GTRecipe.RecipeAssemblyLine(new ItemStack(net.minecraft.init.Items.paper), 1, 32,
            new ItemStack[] { new ItemStack(input) }, new FluidStack[0], new ItemStack(net.minecraft.init.Items.gold_ingot), 100, 32);
        HostedAssemblyRecipe recipe = headlessRecipe(source);
        ItemStack[] stock = { new ItemStack(input, 6) };
        OverclockCalculator calculator = HostedAssemblySupport.calculator(32, 100, 32, 6, 1).setAmperageOC(false);
        ParallelHelper helper = new ParallelHelper().setRecipe(recipe).setItemInputs(stock)
            .setMachine(outputMachine(), false, false)
            .setFluidInputs(new FluidStack[0]).setMaxParallel(6).setAvailableEUt(192)
            .setCalculator(calculator).setConsumption(false).setOutputCalculation(true).build();
        assertTrue(helper.getResult().wasSuccessful());
        assertEquals(6, helper.getCurrentParallel());
        assertEquals(6, stock[0].stackSize);
        assertEquals(6, helper.getItemOutputs()[0].stackSize);
        assertTrue(calculator.getConsumption() <= 192);
        recipe.consumeInput(6, new FluidStack[0], stock);
        assertEquals(0, stock[0].stackSize);
        assertEquals(1, source.mInputs[0].stackSize);
    }

    @Test
    void inadequatePowerDoesNotConsumeInputs() throws Exception {
        Item input = net.minecraft.init.Items.iron_ingot;
        GTRecipe.RecipeAssemblyLine source = new GTRecipe.RecipeAssemblyLine(new ItemStack(net.minecraft.init.Items.paper), 1, 32,
            new ItemStack[] { new ItemStack(input) }, new FluidStack[0], new ItemStack(net.minecraft.init.Items.gold_ingot), 100, 32);
        ItemStack[] stock = { new ItemStack(input, 6) };
        ParallelHelper helper = new ParallelHelper().setRecipe(headlessRecipe(source)).setItemInputs(stock)
            .setFluidInputs(new FluidStack[0]).setMaxParallel(6).setAvailableEUt(31)
            .setCalculator(HostedAssemblySupport.calculator(32, 100, 32, 1, 1))
            .setConsumption(false).setOutputCalculation(true).build();
        assertFalse(helper.getResult().wasSuccessful());
        assertEquals(6, stock[0].stackSize);
    }

    @Test
    void ordinaryAssemblyRunsScreenshotUmvPowerOnlyWhenCombinedHatchesCanSupplyIt() throws Exception {
        int recipeEUt = 125_829_120;
        long voltage = 33_554_432L;
        HostedAssemblyRecipe recipe = headlessRecipe(new GTRecipe.RecipeAssemblyLine(
            new ItemStack(net.minecraft.init.Items.paper), 1, 32,
            new ItemStack[] { new ItemStack(net.minecraft.init.Items.iron_ingot) }, new FluidStack[0],
            new ItemStack(net.minecraft.init.Items.gold_ingot), 1800, recipeEUt));
        for (int amps : new int[] { 2, 4 }) {
            ItemStack[] stock = { new ItemStack(net.minecraft.init.Items.iron_ingot) };
            OverclockCalculator calculator = HostedAssemblySupport.calculator(recipeEUt, 1800, voltage, amps, 1)
                .setAmperageOC(false).setMaxTierSkips(1);
            ParallelHelper helper = HostedAssemblySupport.planBatch(outputMachine(), recipe, voltage * amps, 1,
                stock, new FluidStack[0], calculator, false);
            assertEquals(amps == 4, helper.getResult().wasSuccessful());
            assertEquals(1, stock[0].stackSize);
            if (amps == 4) {
                assertEquals(recipeEUt, calculator.getConsumption());
                assertEquals(1800, calculator.getDuration());
                assertEquals(1, helper.getCurrentParallel());
            }
        }
    }

    @Test
    void nativeProcessingCombinesSubTickSpeedAndRealBatching() throws Exception {
        for (boolean batch : new boolean[] { false, true }) {
            HostedAssemblyRecipe recipe = shortRecipe();
            ItemStack stock = new ItemStack(net.minecraft.init.Items.iron_ingot, 1024);
            ProcessingLogic logic = new ProcessingLogic() {
                @Override
                protected Stream<GTRecipe> findRecipeMatches(RecipeMap<?> map) {
                    return Stream.of(recipe);
                }
            };
            logic.setMachine(outputMachine()).setInputItems(stock).setInputFluids(new FluidStack[0])
                .setAvailableVoltage(512).setAvailableAmperage(1).setMaxParallel(1).setAmperageOC(true);
            try (HostedProcessingSettings settings = new HostedProcessingSettings(logic, 0.25, batch)) {
                assertTrue(logic.process().wasSuccessful());
                assertEquals(batch ? 512 : 4, logic.getCurrentParallels());
                assertEquals(batch ? 128 : 1, logic.getDuration());
                assertEquals(batch ? 512 : 1020, stock.stackSize);
                assertEquals(batch ? 512 : 4, logic.getOutputItems()[0].stackSize);
                assertTrue(logic.getCalculatedEut() <= 512);
            }
            // Reusing a released machine must not keep the host speed or batch mode.
            logic.clear().setInputItems(new ItemStack(net.minecraft.init.Items.iron_ingot, 1024));
            assertTrue(logic.process().wasSuccessful());
            assertEquals(1, logic.getCurrentParallels());
            assertEquals(1, logic.getDuration());
        }
    }

    @Test
    void assemblyBatchPlanningReservesCopiesAndCommitsOnlyAvailableInputs() throws Exception {
        HostedAssemblyRecipe recipe = shortRecipe();
        ItemStack[] stock = { new ItemStack(net.minecraft.init.Items.iron_ingot, 10) };
        OverclockCalculator calculator = HostedAssemblySupport.calculator(32, 4, 512, 1, 1)
            .setDurationModifier(0.25);
        ParallelHelper helper = HostedAssemblySupport.planBatch(outputMachine(), recipe, 512, 1,
            stock, new FluidStack[0], calculator, true);
        assertTrue(helper.getResult().wasSuccessful());
        assertEquals(10, helper.getCurrentParallel());
        assertEquals(10, stock[0].stackSize);
        assertEquals(3, HostedProcessingSettings.duration(calculator.getDuration(), helper.getDurationMultiplierDouble()));
        recipe.plan(stock, new FluidStack[0], helper.getCurrentParallel()).consume(stock, new FluidStack[0]);
        assertEquals(0, stock[0].stackSize);
        assertEquals(10, helper.getItemOutputs()[0].stackSize);
    }

    @Test
    void assemblyWithoutBatchStillRetainsSubTickParallel() throws Exception {
        ItemStack[] stock = { new ItemStack(net.minecraft.init.Items.iron_ingot, 1024) };
        OverclockCalculator calculator = HostedAssemblySupport.calculator(32, 4, 512, 1, 1)
            .setDurationModifier(0.25);
        ParallelHelper helper = HostedAssemblySupport.planBatch(outputMachine(), shortRecipe(), 512, 1,
            stock, new FluidStack[0], calculator, false);
        assertTrue(helper.getResult().wasSuccessful());
        assertEquals(4, helper.getCurrentParallel());
        assertEquals(1, HostedProcessingSettings.duration(calculator.getDuration(), helper.getDurationMultiplierDouble()));
        assertEquals(1024, stock[0].stackSize);
        assertTrue(calculator.getConsumption() <= 512);
    }

    @Test
    void assemblyBatchRejectsInsufficientPowerWithoutConsumingStock() throws Exception {
        ItemStack[] stock = { new ItemStack(net.minecraft.init.Items.iron_ingot, 1024) };
        ParallelHelper helper = HostedAssemblySupport.planBatch(outputMachine(), shortRecipe(), 31, 1,
            stock, new FluidStack[0], HostedAssemblySupport.calculator(32, 4, 32, 1, 1), true);
        assertFalse(helper.getResult().wasSuccessful());
        assertEquals(1024, stock[0].stackSize);
    }

    @Test
    void batchWithExactlyOneShieldingPlateRecipeDoesNotFailOnZeroExtraRuns() throws Exception {
        assertExactBatchWithoutExtraRuns(61_440, 1_000, 1_152);
    }

    @Test
    void batchWithExactlyOneHighVoltageRecipeDoesNotFailOnZeroExtraRuns() throws Exception {
        assertExactBatchWithoutExtraRuns(7_864_320, 400, 256_000);
    }

    @Test
    void zeroConsumptionIsANoOpEvenWithNoRemainingInputs() throws Exception {
        HostedAssemblyRecipe recipe = shortRecipe();
        assertDoesNotThrow(() -> recipe.consumeInput(0, new FluidStack[0], new ItemStack[0]));
    }

    @Test
    void positiveConsumptionStillRejectsInsufficientInputsWithoutPartialRemoval() throws Exception {
        HostedAssemblyRecipe recipe = shortRecipe();
        ItemStack[] stock = { new ItemStack(net.minecraft.init.Items.iron_ingot) };
        assertThrows(IllegalStateException.class, () -> recipe.consumeInput(2, new FluidStack[0], stock));
        assertEquals(1, stock[0].stackSize);
    }

    private void assertExactBatchWithoutExtraRuns(int eut, int duration, int fluidAmount) throws Exception {
        ItemStack[] required = {
            new ItemStack(net.minecraft.init.Items.iron_ingot, 2),
            new ItemStack(net.minecraft.init.Items.gold_ingot, 4),
            new ItemStack(net.minecraft.init.Items.diamond, 4),
            new ItemStack(net.minecraft.init.Items.coal, 4),
            new ItemStack(net.minecraft.init.Items.redstone, 4),
            new ItemStack(net.minecraft.init.Items.glowstone_dust, 4),
            new ItemStack(net.minecraft.init.Items.quartz, 4)
        };
        FluidStack fluid = new FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, fluidAmount);
        HostedAssemblyRecipe recipe = headlessRecipe(new GTRecipe.RecipeAssemblyLine(
            new ItemStack(net.minecraft.init.Items.paper), 1, 32, required,
            new FluidStack[] { fluid }, new ItemStack(net.minecraft.init.Items.emerald), duration, eut));
        recipe.mInputs = required;
        recipe.mFluidInputs = new FluidStack[] { fluid };
        recipe.mFluidInputChances = new int[] { 10000 };
        ItemStack[] stock = java.util.Arrays.stream(required).map(ItemStack::copy).toArray(ItemStack[]::new);
        FluidStack[] fluids = { fluid.copy() };
        long voltage = (long) eut * 64;
        OverclockCalculator calculator = HostedAssemblySupport.calculator(eut, duration, voltage, 1, 1)
            .setAmperageOC(false).setDurationModifier(0.25);
        ParallelHelper helper = HostedAssemblySupport.planBatch(outputMachine(), recipe, voltage, 1,
            stock, fluids, calculator, true);
        assertTrue(helper.getResult().wasSuccessful());
        assertEquals(1, helper.getCurrentParallel());
        assertTrue(calculator.getDuration() < 128);
        assertEquals(1D, helper.getDurationMultiplierDouble());
        assertEquals(1, helper.getItemOutputs()[0].stackSize);
        for (int i = 0; i < stock.length; i++) assertEquals(required[i].stackSize, stock[i].stackSize);
        assertEquals(fluidAmount, fluids[0].amount);
        recipe.consumeInput(1, fluids, stock);
        for (ItemStack item : stock) assertEquals(0, item.stackSize);
        assertEquals(0, fluids[0].amount);
    }

    @Test
    void fusionBatchDoesNotCountDeferredInputsTwice() throws Exception {
        HostedAssemblyRecipe recipe = shortRecipe();
        ItemStack[] stock = { new ItemStack(net.minecraft.init.Items.iron_ingot, 10) };
        java.util.List<Runnable> pending = new java.util.ArrayList<>();
        ParallelHelper helper = HostedFusionSupport.deferInputs(new ParallelHelper().setRecipe(recipe)
            .setItemInputs(stock).setFluidInputs(new FluidStack[0]).setMachine(outputMachine(), false, false)
            .setMaxParallel(4).setAvailableEUt(128)
            .setCalculator(HostedAssemblySupport.calculator(32, 1, 32, 4, 1))
            .enableBatchMode(128).setOutputCalculation(true), pending, stock, new FluidStack[0]).build();
        assertTrue(helper.getResult().wasSuccessful());
        assertEquals(10, helper.getCurrentParallel());
        assertEquals(10, stock[0].stackSize);
        pending.forEach(Runnable::run);
        assertEquals(0, stock[0].stackSize);
        assertEquals(10, helper.getItemOutputs()[0].stackSize);
    }

    private HostedAssemblyRecipe shortRecipe() throws Exception {
        return headlessRecipe(new GTRecipe.RecipeAssemblyLine(new ItemStack(net.minecraft.init.Items.paper), 1, 32,
            new ItemStack[] { new ItemStack(net.minecraft.init.Items.iron_ingot) }, new FluidStack[0],
            new ItemStack(net.minecraft.init.Items.gold_ingot), 4, 32));
    }

    private HostedAssemblyRecipe headlessRecipe(GTRecipe.RecipeAssemblyLine source) throws Exception {
        // Only skip GT's mod-owner/NEI metadata constructor, which requires the complete mod-loading lifecycle.
        // The real overridden matcher, input consumer and native ParallelHelper/OverclockCalculator run below.
        java.lang.reflect.Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) singleton.get(null);
        HostedAssemblyRecipe recipe = (HostedAssemblyRecipe) unsafe.allocateInstance(HostedAssemblyRecipe.class);
        recipe.mInputs = new ItemStack[] { source.mInputs[0].copy() };
        recipe.mOutputs = new ItemStack[] { source.mOutput.copy() };
        recipe.mInputChances = new int[] { 10000 };
        recipe.mOutputChances = new int[] { 10000 };
        recipe.mFluidInputChances = new int[0];
        recipe.mFluidOutputChances = new int[0];
        recipe.mFluidInputs = new FluidStack[0];
        recipe.mFluidOutputs = new FluidStack[0];
        recipe.mDuration = source.mDuration;
        recipe.mEUt = source.mEUt;
        recipe.mEnabled = true;
        return recipe;
    }

    private gregtech.api.interfaces.tileentity.IVoidable outputMachine() {
        return (gregtech.api.interfaces.tileentity.IVoidable) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] { gregtech.api.interfaces.tileentity.IVoidable.class },
            (proxy, method, args) -> {
                if (method.getReturnType() == int.class) return Integer.MAX_VALUE;
                if (method.getReturnType() == boolean.class) return false;
                throw new UnsupportedOperationException(method.getName());
            });
    }
}

package com.gtnhblueprints.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhblueprints.HostedMilestoneConfig;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.util.ExoticEnergyInputHelper;
import gregtech.api.util.GTRecipe.RecipeAssemblyLine;
import gregtech.api.util.GTUtility;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;

final class HostedAssemblySupport {

    private HostedAssemblySupport() {}

    static boolean isAssembly(MTEMultiBlockBase machine) {
        return hasClass(machine, "gregtech.common.tileentities.machines.multi.MTEAssemblyLine") || isAdvanced(machine);
    }

    static boolean isAdvanced(MTEMultiBlockBase machine) {
        return hasClass(machine, "ggfab.mte.MTEAdvAssLine");
    }

    private static boolean hasClass(Object machine, String name) {
        for (Class<?> type = machine == null ? null : machine.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(name)) return true;
        }
        return false;
    }

    static int baseParallel(boolean advanced, int inputCount) {
        if (!advanced) return 1;
        return HostedMilestoneConfig.advancedAssemblyFixedParallel > 0
            ? HostedMilestoneConfig.advancedAssemblyFixedParallel : Math.max(1, Math.min(16, inputCount));
    }

    static int overclockMode(boolean advanced, int energyLevel) {
        if (!advanced) return 1;
        if (energyLevel >= HostedMilestoneConfig.advancedAssemblyPerfectLevel) return 2;
        return energyLevel >= HostedMilestoneConfig.advancedAssemblyNormalLevel ? 1 : 0;
    }

    static OverclockCalculator calculator(int recipeEUt, int duration, long voltage, long amps, int mode) {
        OverclockCalculator calculator = new OverclockCalculator().setRecipeEUt(recipeEUt).setDuration(duration)
            .setEUt(voltage).setAmperage(amps).setAmperageOC(mode != 0);
        if (mode == 0) calculator.setLaserOC(true)
            .setMaxRegularOverclocks(Math.max(0, GTUtility.getTier(voltage) - GTUtility.getTier(recipeEUt)));
        if (mode == 2) calculator.enablePerfectOC();
        return calculator;
    }

    static Prepared prepare(MTEHostedMachineController owner, MTEMultiBlockBase remote, RecipeAssemblyLine source,
        int units, long availablePower, HostedProgression progression, ItemStack[] items, FluidStack[] fluids) {
        boolean advanced = isAdvanced(remote);
        long voltage = Math.max(1, advanced ? remote.getMaxInputVoltage() : remote.getAverageInputVoltage());
        if (!acceptsRecipeVoltage(source.mEUt, voltage, advanced) || source.mDuration <= 0 || source.mOutput == null) return null;
        int efficiency = Math.max(1000, 10000 - (remote.getIdealStatus() - remote.getRepairStatus()) * 1000);
        long rated = ExoticEnergyInputHelper.getTotalEuMulti(remote.getExoticAndNormalEnergyHatchList());
        long capacity = multiply(Math.max(1, rated), units);
        long budget = Math.min(capacity, progression.getNativePowerBudget(availablePower, efficiency));
        if (budget < source.mEUt) return null;
        voltage = Math.min(voltage, budget);
        int base = baseParallel(isAdvanced(remote), source.mInputs.length);
        int parallel = (int) Math.min(Integer.MAX_VALUE, (long) units * base);
        HostedAssemblyRecipe recipe = new HostedAssemblyRecipe(source);
        OverclockCalculator calculator = calculator(source.mEUt, source.mDuration, voltage,
            Math.max(1, budget / voltage), overclockMode(isAdvanced(remote), progression.getEnergyLevel()));
        calculator.setDurationModifier(progression.getConsumerDurationMultiplier());
        calculator.setMaxTierSkips(advanced ? 0 : 1);
        if (!isAdvanced(remote)) calculator.setAmperageOC(false);
        ParallelHelper helper = planBatch(owner, recipe, budget, parallel, items, fluids, calculator,
            owner.isBatchModeEnabled());
        if (!helper.getResult().wasSuccessful() || helper.getCurrentParallel() <= 0) return null;
        long usage = java.math.BigInteger.valueOf(calculator.getConsumption()).multiply(java.math.BigInteger.valueOf(10000))
            .divide(java.math.BigInteger.valueOf(efficiency)).min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        if (progression.applyConsumerPower(usage) > availablePower) return null;
        HostedAssemblyInputs.Plan plan = recipe.plan(items, fluids, helper.getCurrentParallel());
        if (plan == null) return null;
        return new Prepared(helper, calculator, plan, usage, base);
    }

    static boolean acceptsRecipeVoltage(long recipeEUt, long hatchVoltage, boolean advanced) {
        // Ordinary AL permits hatch tier + 1, provided the independent total-power check also passes.
        // Advanced AL requires the recipe tier itself; host voltage-extension is applied before this check.
        long limit = advanced ? hatchVoltage : hatchVoltage > Long.MAX_VALUE / 4 ? Long.MAX_VALUE : hatchVoltage * 4;
        return recipeEUt > 0 && hatchVoltage > 0 && recipeEUt <= limit;
    }

    static ParallelHelper planBatch(IVoidable machine, HostedAssemblyRecipe recipe, long budget, int parallel,
        ItemStack[] items, FluidStack[] fluids, OverclockCalculator calculator, boolean batch) {
        return new ParallelHelper().setRecipe(recipe).setMachine(machine).setAvailableEUt(budget)
            .setMaxParallel(parallel).setItemInputs(items).setFluidInputs(fluids)
            .enableBatchMode(batch ? HostedProcessingSettings.BATCH_SIZE : 1)
            .setCalculator(calculator).setConsumption(false).setOutputCalculation(true).build();
    }

    static ItemStack[] uniqueItems(Iterable<ItemStack> input) {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : input) if (stack != null && stack.stackSize > 0 && seen.add(stack)) result.add(stack);
        return result.toArray(new ItemStack[0]);
    }

    static FluidStack[] uniqueFluids(Iterable<FluidStack> input) {
        Set<FluidStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack stack : input) if (stack != null && stack.amount > 0 && seen.add(stack)) result.add(stack);
        return result.toArray(new FluidStack[0]);
    }

    private static long multiply(long value, int times) {
        return value > Long.MAX_VALUE / Math.max(1, times) ? Long.MAX_VALUE : value * times;
    }

    static final class Prepared {
        final ParallelHelper helper;
        final OverclockCalculator calculator;
        final HostedAssemblyInputs.Plan plan;
        final long usage;
        final int base;

        Prepared(ParallelHelper helper, OverclockCalculator calculator, HostedAssemblyInputs.Plan plan, long usage, int base) {
            this.helper = helper;
            this.calculator = calculator;
            this.plan = plan;
            this.usage = usage;
            this.base = base;
        }
    }
}

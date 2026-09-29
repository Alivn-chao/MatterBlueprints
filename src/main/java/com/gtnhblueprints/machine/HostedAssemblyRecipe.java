package com.gtnhblueprints.machine;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.util.GTRecipe;

/** Private execution recipe; never registered in global recipe maps or written back to data sticks. */
final class HostedAssemblyRecipe extends GTRecipe {
    private final ItemStack[][] alternatives;

    HostedAssemblyRecipe(RecipeAssemblyLine source) {
        super(false, copyItems(source.mInputs), new ItemStack[] { source.mOutput.copy() }, null,
            null, null, null, null, copyFluids(source.mFluidInputs), new FluidStack[0], source.mDuration, source.mEUt, 0);
        // Preserve one entry per original AL ingredient; the normal constructor may unify copies.
        mInputs = copyItems(source.mInputs);
        alternatives = source.mOreDictAlt;
    }

    @Override
    public double maxParallelCalculatedByInputs(int maxParallel, FluidStack[] fluids, ItemStack... items) {
        int low = 0;
        int high = maxParallel;
        while (low < high) {
            int middle = (int) (((long) low + high + 1) / 2);
            if (plan(items, fluids, middle) != null) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    @Override
    public void consumeInput(int parallel, FluidStack[] fluids, ItemStack... items) {
        HostedAssemblyInputs.Plan plan = plan(items, fluids, parallel);
        if (plan == null) throw new IllegalStateException("Assembly inputs changed before commit");
        plan.consume(items, fluids);
    }

    HostedAssemblyInputs.Plan plan(ItemStack[] items, FluidStack[] fluids, int parallel) {
        return HostedAssemblyInputs.plan(mInputs, alternatives, mFluidInputs, items, fluids, parallel);
    }

    private static ItemStack[] copyItems(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < result.length; i++) result[i] = source[i] == null ? null : source[i].copy();
        return result;
    }

    private static FluidStack[] copyFluids(FluidStack[] source) {
        if (source == null) return new FluidStack[0];
        FluidStack[] result = new FluidStack[source.length];
        for (int i = 0; i < result.length; i++) result[i] = source[i] == null ? null : source[i].copy();
        return result;
    }
}

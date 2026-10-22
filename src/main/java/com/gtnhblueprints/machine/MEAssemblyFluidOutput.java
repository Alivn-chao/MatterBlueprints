package com.gtnhblueprints.machine;

import net.minecraftforge.fluids.FluidStack;
import net.minecraft.nbt.NBTTagCompound;
import gregtech.api.enums.OutputHatchType;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.util.GTUtility;

/** Bounded, transactional fluid output; both void protection and real ejection see identical space. */
public final class MEAssemblyFluidOutput implements IOutputHatch {
    final FluidStack[] fluids = new FluidStack[16];
    private final Runnable dirty;
    MEAssemblyFluidOutput(Runnable dirty) { this.dirty = dirty; }
    @Override public boolean isFiltered() { return false; }
    @Override public boolean isFilteredToFluid(GTUtility.FluidId id) { return false; }
    @Override public OutputHatchType getHatchType() { return OutputHatchType.StandardUnfiltered; }
    static FluidStack[] copy(FluidStack[] source) {
        FluidStack[] result = new FluidStack[source.length];
        for (int i = 0; i < source.length; i++) if (source[i] != null) result[i] = source[i].copy();
        return result;
    }
    static boolean insert(FluidStack[] inventory, FluidStack stack) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < inventory.length && stack.amount > 0; i++) {
                FluidStack stored = inventory[i];
                if (pass == 0 ? stored == null || !stored.isFluidEqual(stack) : stored != null && stored.amount > 0) continue;
                int current = stored == null ? 0 : Math.max(0, stored.amount);
                int amount = Math.min(Integer.MAX_VALUE - current, stack.amount);
                if (amount == 0) continue;
                if (stored == null || current == 0) { inventory[i] = stored = stack.copy(); stored.amount = 0; }
                stored.amount += amount;
                stack.amount -= amount;
            }
        }
        return stack.amount == 0;
    }
    @Override public boolean storePartial(FluidStack stack, boolean simulate) {
        boolean result = insert(simulate ? copy(fluids) : fluids, stack);
        if (!simulate) dirty.run();
        return result;
    }
    @Override public IOutputHatchTransaction createTransaction() {
        return new IOutputHatchTransaction() {
            private final FluidStack[] pending = copy(fluids);
            private boolean active = true;
            private void requireActive() { if (!active) throw new IllegalStateException("Output transaction already committed"); }
            @Override public IOutputHatch getHatch() { return MEAssemblyFluidOutput.this; }
            @Override public boolean storePartial(GTUtility.FluidId id, FluidStack stack, long total, long weight) {
                requireActive();
                return insert(pending, stack);
            }
            @Override public void complete(GTUtility.FluidId id) { requireActive(); }
            @Override public boolean hasAvailableSpace() {
                for (FluidStack fluid : pending) if (fluid == null || fluid.amount < Integer.MAX_VALUE) return true;
                return false;
            }
            @Override public void commit() {
                requireActive();
                System.arraycopy(pending, 0, fluids, 0, fluids.length);
                active = false;
                dirty.run();
            }
        };
    }
    void save(NBTTagCompound tag) {
        for (int i = 0; i < fluids.length; i++) if (fluids[i] != null && fluids[i].amount > 0)
            tag.setTag("fluid" + i, fluids[i].writeToNBT(new NBTTagCompound()));
    }
    void load(NBTTagCompound tag) {
        for (int i = 0; i < fluids.length; i++) fluids[i] = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("fluid" + i));
    }
}

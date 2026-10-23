// SPDX-License-Identifier: LGPL-3.0-or-later
package com.gtnhblueprints.machine;

import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.item.AEFluidStack;
import gregtech.api.enums.OutputHatchType;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;
import gregtech.common.tileentities.machines.outputme.util.AECacheCounter;

/** Native GT ME fluid cache and transaction rules, sharing the assembly's single grid node.
 * Transaction adapted from GT5-Unofficial 5.09.54.133 MTEHatchOutputME (LGPL-3.0).
 */
final class MEAssemblyFluidPort implements IOutputHatch, MTEHatchOutputMEBase.Environment<IAEFluidStack> {
    private final MTEMEOutputAssembly owner;
    final ItemStackHandler cell = new ItemStackHandler(1) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack != null && AEApi.instance().registries().cell()
                .getCellInventory(stack, null, StorageChannel.FLUIDS) != null;
        }
        @Override protected void onContentsChanged(int slot) {
            if (provider != null) provider.onContentsChanged(0);
            markDirty();
        }
    };
    final MTEHatchOutputMEBase<IAEFluidStack> provider;
    MEAssemblyFluidPort(MTEMEOutputAssembly owner) {
        this.owner = owner;
        provider = new MTEHatchOutputMEBase<IAEFluidStack>(this, 128_000) {
            @Override public AENetworkProxy getProxy() { return owner.getProxy(); }
        };
    }
    void markDirty() { owner.markDirty(); }
    @Override public IGregTechTileEntity getBaseMetaTileEntity() { return owner.getBaseMetaTileEntity(); }
    @Override public IGridProxyable getIGridProxyable() { return owner; }
    @Override public StorageChannel getChannel() { return StorageChannel.FLUIDS; }
    @Override public ItemStack getCellStack() { return cell.getStackInSlot(0); }
    @Override public ISaveProvider getISaveProvider() { return owner; }
    @Override public BaseActionSource getActionSource() { return owner.getActionSource(); }
    @Override public EntityPlayer getLastClickedPlayer() { return owner.getLastClickedPlayer(); }
    @Override public IMEInventory<IAEFluidStack> getNetworkInvtory() throws GridAccessException {
        return owner.getProxy().getStorage().getFluidInventory();
    }
    @Override public NBTTagCompound saveStackToNBT(IAEFluidStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("fluidStack", stack.getFluidStack().writeToNBT(new NBTTagCompound()));
        tag.setLong("size", stack.getStackSize()); return tag;
    }
    @Override public IAEFluidStack loadStackFromNBT(NBTTagCompound tag) {
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("fluidStack"));
        return fluid == null ? null : AEFluidStack.create(fluid).setStackSize(tag.getLong("size"));
    }
    @Override public byte getColor() { return owner.getColor(); }
    @Override public String getCopiedDataIdentifier(EntityPlayer player) { return "matterblueprints.output.fluids"; }
    @Override public ItemStack getVisual() { return owner.getVisual(); }
    @Override public void dispatchMarkDirty() { markDirty(); }
    @Override public void notifyOutputSpaceChanged() { owner.notifyOutputSpaceChanged(); }
    @Override public MTEHatchOutputMEBase<IAEFluidStack> getProvider() { return provider; }
    @Override public String getEnableKey() { return "GT5U.hatch.fluid.filter.enable"; }
    @Override public String getDisableKey() { return "GT5U.hatch.fluid.filter.disable"; }
    boolean getCheckMode() { return provider.getCheckMode(); }
    boolean shouldCheckCell() { return provider.shouldCheckCell(); }
    @Override public boolean isFiltered() { return provider.isFiltered(); }
    @Override public boolean isFilteredToFluid(GTUtility.FluidId id) { return provider.canStore(AEFluidStack.create(id.getFluidStack())); }
    @Override public OutputHatchType getHatchType() {
        return provider.getCacheMode()
            ? (provider.isFiltered() ? OutputHatchType.MECacheFiltered : OutputHatchType.MECacheUnfiltered)
            : (provider.isFiltered() ? OutputHatchType.MEFiltered : OutputHatchType.MEUnfiltered);
    }
    @Override public boolean storePartial(FluidStack stack, boolean simulate) {
        IAEFluidStack input = AEFluidStack.create(stack);
        provider.storePartial(input, simulate);
        stack.amount = (int) input.getStackSize(); return stack.amount == 0;
    }
    @Override public IOutputHatchTransaction createTransaction() { return new MEOutputHatchTransaction(); }
    void save(NBTTagCompound tag) {
        provider.saveNBTData(tag);
        tag.setTag("drive", cell.serializeNBT());
    }
    void load(NBTTagCompound tag) {
        cell.deserializeNBT(tag.getCompoundTag("drive"));
        if (!tag.hasKey("baseCapacity")) tag.setLong("baseCapacity", 128_000);
        provider.loadNBTData(tag);
        provider.updateState();
    }
    void migrateLegacy(NBTTagCompound tag) {
        MEAssemblyFluidOutput legacy = new MEAssemblyFluidOutput(() -> {});
        legacy.load(tag);
        for (FluidStack fluid : legacy.fluids) if (fluid != null && fluid.amount > 0) provider.addToCache(AEFluidStack.create(fluid));
    }
    class MEOutputHatchTransaction implements IOutputHatchTransaction, IOutputTransaction.IRecipeCheckAware,
        IOutputTransaction.IProtectOutputAware {

        private final AECacheCounter<GTUtility.FluidId> cache = new AECacheCounter<>();
        private final long availableSpace;
        private boolean active = true;
        private boolean allowAnyInput = false;
        private boolean isRecipeCheck = false;
        private boolean isProtectOutput = true;
        private boolean isDynamicCapacity = false;
        private IMEInventoryHandler<IAEFluidStack> cell = null;

        public MEOutputHatchTransaction() {
            availableSpace = provider.getPhysicalSpace();
        }

        public void setRecipeCheck(boolean isRecipeCheck) {
            this.isRecipeCheck = isRecipeCheck;
            if (isRecipeCheck && shouldCheckCell()) {
                provider.flushCachedStack();
                cell = AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory(getCellStack().copy(), getISaveProvider(), getChannel());
            }
            updateFlags();
        }

        public void setProtectOutput(boolean isProtectOutput) {
            this.isProtectOutput = isProtectOutput;
            updateFlags();
        }

        private void updateFlags() {
            // only false when cache mode on and is distribution
            isDynamicCapacity = isRecipeCheck && isProtectOutput
                && getCheckMode()
                && (!provider.getCacheMode() || !provider.isDistribution())
                && !provider.canVoidOverflow();
            allowAnyInput = !getCheckMode() && availableSpace > 0;
            if (!isRecipeCheck) {
                allowAnyInput |= provider.getLastInputTick() == provider.getTickCounter();
            }
        }

        @Override
        public boolean needsTotalParallelData() {
            return isDynamicCapacity;
        }

        @Override
        public IOutputHatch getHatch() {
            return MEAssemblyFluidPort.this;
        }

        @Override
        public boolean hasAvailableSpace() {
            return allowAnyInput || cache.getTotal() < availableSpace;
        }

        @Override
        public boolean storePartial(GTUtility.FluidId id, FluidStack stack, long totalPerParallel,
            long perParallel) {
            if (!active) throw new IllegalStateException("Cannot add to a transaction after committing it");

            if (isRecipeCheck) {
                if (shouldCheckCell()) {
                    IAEFluidStack input = AEFluidStack.create(stack);
                    if (isDynamicCapacity) {
                        long cellAvailableSpace = provider.getCellAvailableSpace();
                        int parallels = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, cellAvailableSpace / totalPerParallel));
                        long amount = Math.min(parallels * perParallel, cellAvailableSpace - cache.getTotal());
                        amount = Math.min(amount, stack.amount);
                        input.setStackSize(amount);
                    }
                    IAEFluidStack rejected = cell.injectItems(input, Actionable.MODULATE, getActionSource());
                    int inserted = (int) (input.getStackSize() - (rejected == null ? 0 : rejected.getStackSize()));
                    cache.insert(id, inserted);
                    stack.amount -= inserted;
                    return inserted > 0;
                } else if (isDynamicCapacity) {
                    int parallels = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, availableSpace / totalPerParallel));
                    long amount = Math.min(parallels * perParallel, availableSpace - cache.getTotal());
                    amount = Math.min(amount, stack.amount);
                    cache.insert(id, amount);
                    stack.amount -= amount;
                    return amount > 0;
                }
            }
            if (!hasAvailableSpace() || !isFilteredTo(id)) {
                return false;
            }
            cache.insert(id, stack.amount);
            stack.amount = 0;
            return true;
        }

        @Override
        public void complete(GTUtility.FluidId id) {
            // Do nothing
        }

        @Override
        public void commit() {
            if (cache.getTotal() > 0) {
                cache.iterateAll(
                    (id, amount) -> {
                        provider.addToCache(
                            AEFluidStack.create(id.getFluidStack())
                                .setStackSize(amount));
                    });
                provider.updateLastInputTick();
                MEAssemblyFluidPort.this.markDirty();
            }
            active = false;
        }
    }

}

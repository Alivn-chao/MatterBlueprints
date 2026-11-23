package com.gtnhblueprints.machine;

import java.util.List;

import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.me.GridAccessException;
import appeng.util.item.AEFluidStack;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.MTEHatchInputME;

public final class MTELongStockInputHatch extends MTEHatchInputME {
    private final boolean advanced;
    private final LongStockInput<FluidStack, IAEFluidStack> stock = new LongStockInput<>(stack -> stack.amount);

    public MTELongStockInputHatch(int id, boolean advanced, String name, String regional) {
        super(id, advanced, name, regional);
        this.advanced = advanced;
    }

    private MTELongStockInputHatch(String name, boolean advanced, int tier, String[] description, ITexture[][][] textures) {
        super(name, advanced, tier, description, textures);
        this.advanced = advanced;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTELongStockInputHatch(mName, advanced, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String getLocalName() {
        return StatCollector.translateToLocal("gt.blockmachines." + mName + ".name");
    }

    @Override
    public void startRecipeProcessing() {
        if (processingRecipe) return;
        stock.clear();
        clearExtractedStacks();
        super.startRecipeProcessing();
    }

    @Override
    public void updateInformationSlot(int index) throws GridAccessException {
        if (!processingRecipe) {
            super.updateInformationSlot(index);
            return;
        }
        if (index < 0 || index >= slots.length || slots[index] == null) return;
        Slot slot = slots[index];
        if (!isAllowedToWork()) {
            slot.resetExtracted();
            return;
        }
        IAEFluidStack key = AEFluidStack.create(slot.config);
        if (key == null) return;
        IAEFluidStack available = LongStockInput.available(getProxy().getStorage().getFluidInventory(), key,
            getRequestSource());
        slot.resetExtracted();
        if (available == null || available.getStackSize() <= 0) return;
        int count = (int) Math.min(available.getStackSize(), Integer.MAX_VALUE);
        slot.extracted = GTUtility.copyAmount(count, slot.config);
        slot.extractedAmount = count;
        stock.remember(slot.extracted, key, available.getStackSize());
    }

    public void appendLongInputs(List<FluidStack> inputs) {
        if (processingRecipe) stock.appendExtra(inputs, GTUtility::copyAmount);
    }

    long getDisplayAmount(int index) {
        Slot slot = slots[index];
        if (slot == null || getBaseMetaTileEntity() == null || !getBaseMetaTileEntity().isServerSide()
            || !isAllowedToWork()) return 0L;
        IAEFluidStack key = AEFluidStack.create(slot.config);
        if (key == null) return 0L;
        try {
            IAEFluidStack available = LongStockInput.available(getProxy().getStorage().getFluidInventory(), key,
                getRequestSource());
            return available == null ? 0L : available.getStackSize();
        } catch (GridAccessException unavailable) {
            return 0L;
        }
    }

    FluidStack getDisplayFluid(int index) {
        Slot slot = slots[index];
        return slot == null ? null : slot.getOriginalExtracted();
    }

    private BaseActionSource getRequestSource() {
        if (requestSource == null) requestSource = new MachineSource((IActionHost) getBaseMetaTileEntity());
        return requestSource;
    }

    @Override
    public CheckRecipeResult endRecipeProcessing(MTEMultiBlockBase controller) {
        processingRecipe = false;
        return stock.settle(controller, getProxy(), () -> getProxy().getStorage().getFluidInventory(),
            getRequestSource(), "stocking_hatch_fail_extraction");
    }
}

package com.gtnhblueprints.machine;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

public final class MTELongStockInputBus extends MTEHatchInputBusME {
    private final boolean advanced;
    private final LongStockInput<ItemStack, IAEItemStack> stock = new LongStockInput<>(stack -> stack.stackSize);

    public MTELongStockInputBus(int id, boolean advanced, String name, String regional) {
        super(id, advanced, name, regional);
        this.advanced = advanced;
    }

    private MTELongStockInputBus(String name, boolean advanced, int tier, String[] description, ITexture[][][] textures) {
        super(name, advanced, tier, description, textures);
        this.advanced = advanced;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTELongStockInputBus(mName, advanced, mTier, mDescriptionArray, mTextures);
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
        IAEItemStack key = slot.createAEStack(1).copy();
        IAEItemStack available = LongStockInput.available(getProxy().getStorage().getItemInventory(), key,
            getRequestSource());
        slot.resetExtracted();
        if (available == null || available.getStackSize() <= 0) return;
        int count = (int) Math.min(available.getStackSize(), Integer.MAX_VALUE);
        slot.extracted = recipeStack(count, slot.config);
        slot.extractedAmount = count;
        stock.remember(slot.extracted, key, available.getStackSize());
    }

    public void appendLongInputs(List<ItemStack> inputs) {
        if (processingRecipe) stock.appendExtra(inputs, MTELongStockInputBus::recipeStack);
    }

    long getDisplayAmount(int index) {
        Slot slot = slots[index];
        if (slot == null || getBaseMetaTileEntity() == null || !getBaseMetaTileEntity().isServerSide()
            || !isAllowedToWork()) return 0L;
        try {
            IAEItemStack available = LongStockInput.available(getProxy().getStorage().getItemInventory(),
                slot.createAEStack(1), getRequestSource());
            return available == null ? 0L : available.getStackSize();
        } catch (GridAccessException unavailable) {
            return 0L;
        }
    }

    static ItemStack recipeStack(int count, ItemStack template) {
        ItemStack stack = template.copy();
        stack.stackSize = count;
        return stack;
    }

    @Override
    public CheckRecipeResult endRecipeProcessing(MTEMultiBlockBase controller) {
        processingRecipe = false;
        return stock.settle(controller, getProxy(), () -> getProxy().getStorage().getItemInventory(),
            getRequestSource(), "stocking_bus_fail_extraction");
    }
}

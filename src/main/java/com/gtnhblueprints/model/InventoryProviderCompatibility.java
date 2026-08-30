package com.gtnhblueprints.model;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.recursive_pineapple.matter_manipulator.common.building.InventoryAnalysis;
import com.recursive_pineapple.matter_manipulator.common.building.PortableItemStack;
import com.recursive_pineapple.matter_manipulator.common.building.providers.IItemProvider;

/** Compatibility rules for inventory items whose runtime NBT is not a craftable AE output. */
public final class InventoryProviderCompatibility {

    private static final String VOLUMETRIC_FLASK_CLASS = "gregtech.common.items.ItemVolumetricFlask";

    private InventoryProviderCompatibility() {}

    public static void normalize(InventoryAnalysis inventory) {
        if (inventory == null || inventory.mItems == null) return;
        for (int i = 0; i < inventory.mItems.length; i++) {
            IItemProvider normalized = normalize(inventory.mItems[i]);
            if (normalized != null) inventory.mItems[i] = normalized;
        }
    }

    static IItemProvider normalize(IItemProvider provider) {
        if (provider instanceof ConfiguredInventoryItemProvider) return provider;
        if (!(provider instanceof PortableItemStack)) return provider;

        ItemStack original = ((PortableItemStack) provider).toStack();
        if (!isVolumetricFlask(original)) return provider;

        FluidStack fluid = null;
        if (original.getItem() instanceof IFluidContainerItem) {
            fluid = ((IFluidContainerItem) original.getItem()).getFluid(original);
        }

        ItemStack required = original.copy();
        required.setTagCompound(null);

        ItemStack configured = original.copy();
        NBTTagCompound configuredTag = configured.getTagCompound();
        if (configuredTag != null) {
            configuredTag.removeTag("Fluid");
            if (configuredTag.hasNoTags()) configured.setTagCompound(null);
        }

        return new ConfiguredInventoryItemProvider(
            PortableItemStack.withNBT(required),
            PortableItemStack.withNBT(configured),
            fluid);
    }

    private static boolean isVolumetricFlask(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        return item != null && VOLUMETRIC_FLASK_CLASS.equals(item.getClass().getName());
    }
}

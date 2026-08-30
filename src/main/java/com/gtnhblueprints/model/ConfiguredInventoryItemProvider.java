package com.gtnhblueprints.model;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.recursive_pineapple.matter_manipulator.common.building.IPseudoInventory;
import com.recursive_pineapple.matter_manipulator.common.building.PortableItemStack;
import com.recursive_pineapple.matter_manipulator.common.building.providers.IItemProvider;

/**
 * Consumes a normally craftable item and returns the harmlessly configured form
 * that should be inserted into a copied inventory.
 */
public final class ConfiguredInventoryItemProvider implements IItemProvider {

    private final PortableItemStack required;
    private final PortableItemStack configured;
    private final FluidStack fluid;

    public ConfiguredInventoryItemProvider(PortableItemStack required, PortableItemStack configured) {
        this(required, configured, null);
    }

    public ConfiguredInventoryItemProvider(
        PortableItemStack required,
        PortableItemStack configured,
        FluidStack fluid
    ) {
        this.required = required;
        this.configured = configured;
        this.fluid = fluid == null ? null : fluid.copy();
    }

    @Override
    public ItemStack getStack(IPseudoInventory inventory, boolean consume) {
        ItemStack output = createOutput();
        if (!consume || output == null) return output;

        ItemStack input = required == null ? null : required.toStack();
        if (input == null || inventory == null) return null;

        // The MM requirement scanner uses the provider as a pseudo-consumer. It must
        // see the craftable empty flask, but must never touch the live ME network.
        if (AeFluidAccess.isRequirementScan(inventory)) {
            return inventory.tryConsumeItems(input) ? output : null;
        }

        AeFluidAccess extraction = null;
        if (fluid != null && fluid.amount > 0) {
            extraction = AeFluidAccess.prepare(inventory, fluid);
            if (extraction == null || !extraction.hasEnough()) {
                warnMissingFluid(inventory);
                return null;
            }
        }

        if (!inventory.tryConsumeItems(input)) return null;
        if (extraction != null && !extraction.extract()) {
            inventory.givePlayerItems(input);
            warnMissingFluid(inventory);
            return null;
        }
        return output;
    }

    @Override
    public ConfiguredInventoryItemProvider clone() {
        return new ConfiguredInventoryItemProvider(
            required == null ? null : required.clone(),
            configured == null ? null : configured.clone(),
            fluid);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other instanceof ConfiguredInventoryItemProvider) {
            return sameStack(createOutput(), ((ConfiguredInventoryItemProvider) other).createOutput());
        }
        if (other instanceof PortableItemStack) return sameStack(createOutput(), ((PortableItemStack) other).toStack());
        return false;
    }

    @Override
    public int hashCode() {
        ItemStack stack = createOutput();
        if (stack == null) return 0;
        int result = 31 * System.identityHashCode(stack.getItem()) + stack.getItemDamage();
        result = 31 * result + stack.stackSize;
        return 31 * result + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
    }

    private ItemStack createOutput() {
        ItemStack output = configured == null ? null : configured.toStack();
        if (output == null || fluid == null || fluid.amount <= 0) return output;
        if (!(output.getItem() instanceof IFluidContainerItem)) return null;
        IFluidContainerItem container = (IFluidContainerItem) output.getItem();
        return container.fill(output, fluid.copy(), true) == fluid.amount ? output : null;
    }

    private void warnMissingFluid(IPseudoInventory inventory) {
        if (!(inventory instanceof com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext)) {
            return;
        }
        String name = fluid == null ? "未知流体" : fluid.getLocalizedName();
        ((com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext) inventory).warn(
            new ChatComponentText("§c[GTBP] AE 中缺少 " + name + " × " + (fluid == null ? 0 : fluid.amount) + " mB"));
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage() && a.stackSize == b.stackSize
            && ItemStack.areItemStackTagsEqual(a, b);
    }
}

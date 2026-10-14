package com.gtnhblueprints.machine;

import java.util.List;
import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.util.GTRecipe.RecipeAssemblyLine;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/** Resolves overlapping researched recipes against one input buffer before any inputs are consumed. */
final class HostedAssemblySelection {
    private HostedAssemblySelection() {}

    static List<RecipeAssemblyLine> forInventory(List<RecipeAssemblyLine> researched, IDualInputInventory inventory) {
        if (!(inventory instanceof PatternSlot<?>)) return researched;
        return forPattern(researched, ((PatternSlot<?>) inventory).getPatternDetails());
    }

    static List<RecipeAssemblyLine> forPattern(List<RecipeAssemblyLine> researched, ICraftingPatternDetails pattern) {
        List<RecipeAssemblyLine> selected = new ArrayList<>();
        if (pattern == null) return selected;
        IAEStack<?>[] outputs = pattern.getAEOutputs();
        if (outputs == null) return selected;
        ItemStack target = null;
        for (IAEStack<?> output : outputs) {
            if (output == null || output.getStackSize() <= 0) continue;
            // Assembly-line recipes have one item output; a mixed or fluid-output pattern cannot authorize one.
            if (!(output instanceof IAEItemStack)) return selected;
            ItemStack item = ((IAEItemStack) output).getItemStack();
            if (item == null || item.getItem() == null) return selected;
            if (target != null && !sameItem(target, item)) return selected;
            target = item;
        }
        if (target == null) return selected;
        for (RecipeAssemblyLine recipe : researched) {
            if (sameItem(target, recipe.mOutput)) selected.add(recipe);
        }
        return selected;
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        // Pattern multipliers can change amounts; metadata and NBT still identify the requested product exactly.
        return right != null && left.getItem() == right.getItem() && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    static boolean mayStart(RecipeAssemblyLine candidate, List<RecipeAssemblyLine> researched,
        ItemStack[] items, FluidStack[] fluids) {
        if (!researched.contains(candidate)) return false;
        if (plan(candidate, items, fluids) == null) return false;
        for (RecipeAssemblyLine other : researched) {
            if (other == candidate || ItemStack.areItemStacksEqual(candidate.mOutput, other.mOutput)) continue;
            HostedAssemblyInputs.Plan otherPlan = plan(other, items, fluids);
            if (otherPlan == null) continue;
            // Re-match against only what the other recipe would consume. This handles unordered inputs,
            // split stacks, duplicate ingredients and ore alternatives without relying on slot order or totals.
            ItemStack[] reservedItems = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                if (otherPlan.items[i] <= 0) continue;
                reservedItems[i] = items[i].copy();
                reservedItems[i].stackSize = (int) otherPlan.items[i];
            }
            FluidStack[] reservedFluids = new FluidStack[fluids.length];
            for (int i = 0; i < fluids.length; i++) {
                if (otherPlan.fluids[i] <= 0) continue;
                reservedFluids[i] = fluids[i].copy();
                reservedFluids[i].amount = (int) otherPlan.fluids[i];
            }
            // A strict subset must wait for the more specific recipe, even if it cannot start due to power
            // or output space. Equal inputs with different outputs are ambiguous: neither may consume them.
            if (plan(candidate, reservedItems, reservedFluids) != null) return false;
        }
        return true;
    }

    private static HostedAssemblyInputs.Plan plan(RecipeAssemblyLine recipe, ItemStack[] items, FluidStack[] fluids) {
        if (recipe.mEUt <= 0 || recipe.mDuration <= 0 || recipe.mOutput == null) return null;
        return HostedAssemblyInputs.plan(recipe.mInputs, recipe.mOreDictAlt,
            recipe.mFluidInputs == null ? new FluidStack[0] : recipe.mFluidInputs, items, fluids, 1);
    }
}

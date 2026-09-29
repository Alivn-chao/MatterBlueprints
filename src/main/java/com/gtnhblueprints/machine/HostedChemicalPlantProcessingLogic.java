package com.gtnhblueprints.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;

/** Matches the 5.09.54.132 chemical plant checks, intentionally without its catalyst wear callback. */
final class HostedChemicalPlantProcessingLogic extends ProcessingLogic {

    private final Predicate<ItemStack> isCatalyst;
    private final List<ItemStack> catalysts = new ArrayList<ItemStack>();
    private int casingTier;

    HostedChemicalPlantProcessingLogic(Predicate<ItemStack> isCatalyst) {
        this.isCatalyst = isCatalyst;
    }

    void configure(int casingTier, List<ItemStack> inputs) {
        this.casingTier = casingTier;
        catalysts.clear();
        for (ItemStack stack : inputs) {
            if (stack != null && stack.stackSize > 0 && isCatalyst.test(stack)) catalysts.add(stack.copy());
        }
    }

    @Override
    protected CheckRecipeResult validateRecipe(GTRecipe recipe) {
        return checkRequirements(recipe.mSpecialValue, recipe.mInputs);
    }

    CheckRecipeResult checkRequirements(int requiredTier, ItemStack[] inputs) {
        if (requiredTier > casingTier) {
            return CheckRecipeResultRegistry.insufficientMachineTier(requiredTier + 1);
        }
        for (ItemStack required : inputs) {
            if (required == null || !isCatalyst.test(required)) continue;
            boolean present = false;
            for (ItemStack available : catalysts) {
                // GT's catalyst match ignores durability NBT. Never accept an ordinary input bus as the source.
                if (available.isItemEqual(required)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                return SimpleCheckRecipeResult.ofFailure("no_catalyst");
            }
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public ItemStack[] prepareCatalyst(ItemStack[] inputs) {
        List<ItemStack> result = new ArrayList<ItemStack>(Arrays.asList(inputs));
        for (ItemStack stack : catalysts) result.add(stack.copy());
        return result.toArray(new ItemStack[0]);
    }
}

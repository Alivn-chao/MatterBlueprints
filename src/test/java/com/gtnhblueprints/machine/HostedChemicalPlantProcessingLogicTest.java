package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class HostedChemicalPlantProcessingLogicTest {

    private final Item catalystItem = new Item();
    private final Item ingredientItem = new Item();

    private HostedChemicalPlantProcessingLogic logic() {
        return new HostedChemicalPlantProcessingLogic(stack -> stack.getItem() == catalystItem);
    }

    @Test
    void catalystCopiesCannotConsumeInventoryOrDamageNbt() {
        HostedChemicalPlantProcessingLogic logic = logic();
        ItemStack stored = new ItemStack(catalystItem, 1, 3);
        stored.setTagCompound(new NBTTagCompound());
        stored.getTagCompound().setInteger("damage", 49);
        logic.configure(4, Collections.singletonList(stored));
        ItemStack ingredient = new ItemStack(ingredientItem, 64);
        ItemStack[] prepared = logic.prepareCatalyst(new ItemStack[] { ingredient });
        assertSame(ingredient, prepared[0]);
        assertNotSame(stored, prepared[1]);
        prepared[1].stackSize = 0;
        prepared[1].getTagCompound().setInteger("damage", 50);
        assertEquals(1, stored.stackSize);
        assertEquals(49, stored.getTagCompound().getInteger("damage"));
        ItemStack again = logic.prepareCatalyst(new ItemStack[0])[0];
        assertEquals(1, again.stackSize);
        assertEquals(49, again.getTagCompound().getInteger("damage"));
    }

    @Test
    void catalystMustExistInCentralHousingNotOrdinaryInputs() {
        HostedChemicalPlantProcessingLogic logic = logic();
        logic.configure(4, Collections.<ItemStack>emptyList());
        assertFalse(logic.checkRequirements(4, new ItemStack[] { new ItemStack(catalystItem, 0, 2) }).wasSuccessful());
        assertTrue(logic.checkRequirements(4, new ItemStack[] { new ItemStack(ingredientItem) }).wasSuccessful());
    }

    @Test
    void catalystTypeAndOriginalPlantTierAreRequired() {
        HostedChemicalPlantProcessingLogic logic = logic();
        logic.configure(4, Collections.singletonList(new ItemStack(catalystItem, 1, 2)));
        assertTrue(logic.checkRequirements(4, new ItemStack[] { new ItemStack(catalystItem, 0, 2) }).wasSuccessful());
        assertFalse(logic.checkRequirements(4, new ItemStack[] { new ItemStack(catalystItem, 0, 3) }).wasSuccessful());
        assertFalse(logic.checkRequirements(5, new ItemStack[] { new ItemStack(catalystItem, 0, 2) }).wasSuccessful());
    }

    @Test
    void removalIsSeenOnNextJobDespiteReusingRecipeLogic() {
        HostedChemicalPlantProcessingLogic logic = logic();
        ItemStack required = new ItemStack(catalystItem, 0, 2);
        logic.configure(4, Collections.singletonList(new ItemStack(catalystItem, 1, 2)));
        assertTrue(logic.checkRequirements(4, new ItemStack[] { required }).wasSuccessful());
        logic.configure(4, Collections.<ItemStack>emptyList());
        assertFalse(logic.checkRequirements(4, new ItemStack[] { required }).wasSuccessful());
        assertEquals(0, logic.prepareCatalyst(new ItemStack[0]).length);
    }

    @Test
    void emptyAndNonCatalystStacksNeverAuthorizeRecipes() {
        HostedChemicalPlantProcessingLogic logic = logic();
        logic.configure(4, Arrays.asList(null, new ItemStack(catalystItem, 0), new ItemStack(ingredientItem)));
        assertEquals(0, logic.prepareCatalyst(new ItemStack[0]).length);
        assertFalse(logic.checkRequirements(4, new ItemStack[] { new ItemStack(catalystItem, 0) }).wasSuccessful());
    }
}

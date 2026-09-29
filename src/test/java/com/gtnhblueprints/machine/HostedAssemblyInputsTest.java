package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

class HostedAssemblyInputsTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() throws Exception {
        AssemblyTestBootstrap.initialize();
    }

    private static final FluidStack[] NONE = new FluidStack[0];

    @Test
    void sixUnorderedIngredientsConsumeSixRecipesIncludingFluid() {
        ItemStack[] recipe = new ItemStack[6];
        ItemStack[] stock = new ItemStack[6];
        for (int i = 0; i < 6; i++) {
            recipe[i] = new ItemStack(new Item(), 1);
            stock[5 - i] = new ItemStack(recipe[i].getItem(), 8);
        }
        Fluid water = new Fluid("assembly_test_water");
        net.minecraftforge.fluids.FluidRegistry.registerFluid(water);
        FluidStack[] fluids = { new FluidStack(water, 700) };
        HostedAssemblyInputs.Plan plan = HostedAssemblyInputs.plan(recipe, null,
            new FluidStack[] { new FluidStack(water, 100) }, stock, fluids, 6);
        assertNotNull(plan);
        assertEquals(8, stock[0].stackSize);
        plan.consume(stock, fluids);
        for (ItemStack stack : stock) assertEquals(2, stack.stackSize);
        assertEquals(100, fluids[0].amount);
    }

    @Test
    void overlappingAlternativesDoNotStealTheOnlyExactIngredient() {
        Item a = new Item();
        Item b = new Item();
        ItemStack[] recipe = { new ItemStack(a), new ItemStack(a) };
        ItemStack[][] alternatives = { { new ItemStack(a), new ItemStack(b) }, null };
        ItemStack[] stock = { new ItemStack(a), new ItemStack(b) };
        HostedAssemblyInputs.Plan plan = HostedAssemblyInputs.plan(recipe, alternatives, NONE, stock, NONE, 1);
        assertNotNull(plan);
        plan.consume(stock, NONE);
        assertEquals(0, stock[0].stackSize);
        assertEquals(0, stock[1].stackSize);
    }

    @Test
    void duplicateIngredientsCannotSpendTheSameStockTwice() {
        Item a = new Item();
        ItemStack[] recipe = { new ItemStack(a, 3), new ItemStack(a, 3) };
        ItemStack[] stock = { new ItemStack(a, 5) };
        assertNull(HostedAssemblyInputs.plan(recipe, null, NONE, stock, NONE, 1));
        assertEquals(5, stock[0].stackSize);
    }

    @Test
    void alternateQuantityAndSplitStacksArePreserved() {
        Item a = new Item();
        Item b = new Item();
        ItemStack[] stock = { new ItemStack(b, 3), new ItemStack(b, 5) };
        HostedAssemblyInputs.Plan plan = HostedAssemblyInputs.plan(new ItemStack[] { new ItemStack(a) },
            new ItemStack[][] { { new ItemStack(a), new ItemStack(b, 4) } }, NONE, stock, NONE, 2);
        assertNotNull(plan);
        plan.consume(stock, NONE);
        assertEquals(0, stock[0].stackSize + stock[1].stackSize);
    }

    @Test
    void missingFluidConsumesNoItemsAndDuplicateFluidsShareStock() {
        ItemStack[] items = { new ItemStack(new Item(), 5) };
        Fluid fluid = new Fluid("assembly_test_shared_fluid");
        net.minecraftforge.fluids.FluidRegistry.registerFluid(fluid);
        FluidStack[] stock = { new FluidStack(fluid, 100) };
        assertNull(HostedAssemblyInputs.plan(new ItemStack[] { items[0].copy() }, null,
            new FluidStack[] { new FluidStack(fluid, 60), new FluidStack(fluid, 60) }, items, stock, 1));
        assertEquals(5, items[0].stackSize);
        assertEquals(100, stock[0].amount);
    }
}

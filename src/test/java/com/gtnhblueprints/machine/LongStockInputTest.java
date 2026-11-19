package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

class LongStockInputTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        AssemblyTestBootstrap.initialize();
    }

    @Test
    void gtRecipeCanConsumeMoreThanIntMaxFromBothMeInputTypes() throws Exception {
        int parallels = 20_000_000;
        long available = 3_000_000_000L;
        LongStockInput<ItemStack, IAEItemStack> itemStock = new LongStockInput<>(stack -> stack.stackSize);
        LongStockInput<FluidStack, IAEFluidStack> fluidStock = new LongStockInput<>(stack -> stack.amount);
        ItemStack firstItems = new ItemStack(Items.iron_ingot, Integer.MAX_VALUE);
        FluidStack firstFluids = new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE);
        itemStock.remember(firstItems, null, available);
        fluidStock.remember(firstFluids, null, available);
        List<ItemStack> items = new ArrayList<>(Collections.singletonList(firstItems));
        List<FluidStack> fluids = new ArrayList<>(Collections.singletonList(firstFluids));
        itemStock.appendExtra(items, MTELongStockInputBus::recipeStack);
        fluidStock.appendExtra(fluids, GTUtility::copyAmount);
        assertTrue(itemStock.consumption().isEmpty());
        assertTrue(fluidStock.consumption().isEmpty());

        // Bypass mod metadata initialization while exercising the real GT matcher and consumer.
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        GTRecipe recipe = (GTRecipe) ((sun.misc.Unsafe) singleton.get(null)).allocateInstance(GTRecipe.class);
        recipe.mInputs = new ItemStack[] { new ItemStack(Items.iron_ingot, 128) };
        recipe.mFluidInputs = new FluidStack[] { new FluidStack(FluidRegistry.WATER, 144) };
        ItemStack[] itemInputs = items.toArray(new ItemStack[0]);
        FluidStack[] fluidInputs = fluids.toArray(new FluidStack[0]);
        assertEquals((double) parallels, recipe.maxParallelCalculatedByInputs(parallels, fluidInputs, itemInputs));
        recipe.consumeInput(parallels, fluidInputs, itemInputs);
        assertEquals(2_560_000_000L, itemStock.consumption().get(null).longValue());
        assertEquals(2_880_000_000L, fluidStock.consumption().get(null).longValue());
    }

    @Test
    void meDeduplicationDoesNotExposeStockFromAnUnselectedBus() {
        ItemStack primary = new ItemStack(Items.iron_ingot, Integer.MAX_VALUE);
        LongStockInput<ItemStack, IAEItemStack> stock = new LongStockInput<>(stack -> stack.stackSize);
        stock.remember(primary, null, Long.MAX_VALUE);
        List<ItemStack> inputs = new ArrayList<>(Collections.singletonList(primary.copy()));
        stock.appendExtra(inputs, MTELongStockInputBus::recipeStack);
        assertEquals(1, inputs.size());

        inputs.set(0, primary);
        stock.appendExtra(inputs, MTELongStockInputBus::recipeStack);
        assertEquals(LongStockInput.WINDOW, inputs.stream().mapToLong(stack -> stack.stackSize).sum());
    }
}

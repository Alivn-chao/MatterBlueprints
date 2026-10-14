package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import gregtech.api.util.GTRecipe.RecipeAssemblyLine;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

class HostedAssemblySelectionTest {
    private static Fluid solder, argon, helium;

    @BeforeAll
    static void bootstrap() throws Exception {
        AssemblyTestBootstrap.initialize();
        solder = fluid("selection_solder");
        argon = fluid("selection_argon");
        helium = fluid("selection_helium");
    }

    @Test
    void beamOutputMaterialsDoNotRunBeamInputRegardlessOfStickOrder() {
        RecipeAssemblyLine input = beam(false);
        RecipeAssemblyLine output = beam(true);
        ItemStack[] stock = copy(output.mInputs);
        FluidStack[] fluids = copy(output.mFluidInputs);
        for (List<RecipeAssemblyLine> order : Arrays.asList(Arrays.asList(input, output), Arrays.asList(output, input))) {
            assertFalse(HostedAssemblySelection.mayStart(input, order, stock, fluids));
            assertTrue(HostedAssemblySelection.mayStart(output, order, stock, fluids));
        }
        for (int i = 0; i < stock.length; i++) assertTrue(ItemStack.areItemStacksEqual(output.mInputs[i], stock[i]));
        for (int i = 0; i < fluids.length; i++) assertEquals(output.mFluidInputs[i].amount, fluids[i].amount);
    }

    @Test
    void separatePatternBuffersKeepTheirOwnRecipe() {
        RecipeAssemblyLine input = beam(false);
        RecipeAssemblyLine output = beam(true);
        List<RecipeAssemblyLine> recipes = Arrays.asList(input, output);
        assertTrue(HostedAssemblySelection.mayStart(input, recipes, copy(input.mInputs), copy(input.mFluidInputs)));
        assertFalse(HostedAssemblySelection.mayStart(output, recipes, copy(input.mInputs), copy(input.mFluidInputs)));
        assertFalse(HostedAssemblySelection.mayStart(input, recipes, copy(output.mInputs), copy(output.mFluidInputs)));
        assertTrue(HostedAssemblySelection.mayStart(output, recipes, copy(output.mInputs), copy(output.mFluidInputs)));
    }

    @Test
    void noFallbackToSubsetWhenLargerRecipeNeedsMorePower() {
        RecipeAssemblyLine input = beam(false);
        RecipeAssemblyLine output = beam(true);
        output.mEUt = Integer.MAX_VALUE;
        assertFalse(HostedAssemblySelection.mayStart(input, Arrays.asList(input, output),
            copy(output.mInputs), copy(output.mFluidInputs)));
    }

    @Test
    void onlyResearchedRecipesParticipateInSelection() {
        RecipeAssemblyLine input = beam(false);
        RecipeAssemblyLine output = beam(true);
        assertTrue(HostedAssemblySelection.mayStart(input, Collections.singletonList(input),
            copy(output.mInputs), copy(output.mFluidInputs)));
    }

    @Test
    void equalInputsAndDifferentOutputsWaitInsteadOfDependingOnOrder() {
        RecipeAssemblyLine first = beam(false);
        RecipeAssemblyLine second = beam(false);
        second.mOutput = beam(true).mOutput;
        List<RecipeAssemblyLine> recipes = Arrays.asList(first, second);
        assertFalse(HostedAssemblySelection.mayStart(first, recipes, copy(first.mInputs), copy(first.mFluidInputs)));
        assertFalse(HostedAssemblySelection.mayStart(second, recipes, copy(first.mInputs), copy(first.mFluidInputs)));
    }

    @Test
    void duplicateRecipesWithTheSameOutputDoNotBlockEachOther() {
        RecipeAssemblyLine first = beam(false);
        RecipeAssemblyLine second = beam(false);
        assertTrue(HostedAssemblySelection.mayStart(first, Arrays.asList(first, second),
            copy(first.mInputs), copy(first.mFluidInputs)));
    }

    @Test
    void unorderedSplitStacksAndCircuitAlternativesStillPreferBeamOutput() {
        RecipeAssemblyLine input = beam(false);
        RecipeAssemblyLine output = beam(true);
        input.mOreDictAlt[1] = new ItemStack[] { new ItemStack(Items.redstone, 2), new ItemStack(Items.glowstone_dust, 2) };
        output.mOreDictAlt[1] = new ItemStack[] { new ItemStack(Items.redstone, 6), new ItemStack(Items.glowstone_dust, 6) };
        ItemStack[] stock = Arrays.copyOf(copy(output.mInputs), output.mInputs.length + 1);
        stock[1] = new ItemStack(Items.glowstone_dust, 3);
        stock[stock.length - 1] = new ItemStack(Items.glowstone_dust, 3);
        Collections.reverse(Arrays.asList(stock));
        FluidStack[] fluids = copy(output.mFluidInputs);
        Collections.reverse(Arrays.asList(fluids));
        assertFalse(HostedAssemblySelection.mayStart(input, Arrays.asList(input, output), stock, fluids));
        assertTrue(HostedAssemblySelection.mayStart(output, Arrays.asList(input, output), stock, fluids));
    }

    @Test
    void unrelatedSimultaneouslyAvailableRecipesDoNotBlockEachOther() {
        RecipeAssemblyLine input = beam(false);
        RecipeAssemblyLine other = beam(false);
        other.mInputs = new ItemStack[] { new ItemStack(Items.emerald) };
        other.mOreDictAlt = new ItemStack[1][];
        other.mFluidInputs = new FluidStack[0];
        other.mOutput = new ItemStack(Items.apple);
        ItemStack[] stock = Arrays.copyOf(copy(input.mInputs), input.mInputs.length + 1);
        stock[stock.length - 1] = new ItemStack(Items.emerald);
        assertTrue(HostedAssemblySelection.mayStart(input, Arrays.asList(input, other), stock, copy(input.mFluidInputs)));
        assertTrue(HostedAssemblySelection.mayStart(other, Arrays.asList(input, other), stock, copy(input.mFluidInputs)));
    }

    @Test
    void patternTargetWinsEvenWhenIngredientsAlsoMatchAnotherRecipe() {
        RecipeAssemblyLine input = beam(false);
        RecipeAssemblyLine output = beam(true);
        List<RecipeAssemblyLine> researched = Arrays.asList(input, output);
        // Intent comes from the pattern, even if the buffer holds surplus/shared ingredients for the larger recipe.
        List<RecipeAssemblyLine> inputPattern = HostedAssemblySelection.forPattern(researched, pattern(input.mOutput));
        assertTrue(HostedAssemblySelection.mayStart(input, inputPattern, copy(output.mInputs), copy(output.mFluidInputs)));
        assertFalse(HostedAssemblySelection.mayStart(output, inputPattern, copy(output.mInputs), copy(output.mFluidInputs)));
        List<RecipeAssemblyLine> outputPattern = HostedAssemblySelection.forPattern(researched, pattern(output.mOutput));
        assertFalse(HostedAssemblySelection.mayStart(input, outputPattern, copy(output.mInputs), copy(output.mFluidInputs)));
        assertTrue(HostedAssemblySelection.mayStart(output, outputPattern, copy(output.mInputs), copy(output.mFluidInputs)));
    }

    @Test
    void incompleteOrUnresearchedTargetNeverFallsBackToAnotherOutput() {
        RecipeAssemblyLine input = beam(false);
        RecipeAssemblyLine output = beam(true);
        List<RecipeAssemblyLine> selected = HostedAssemblySelection.forPattern(Arrays.asList(input, output), pattern(output.mOutput));
        assertFalse(HostedAssemblySelection.mayStart(input, selected, copy(input.mInputs), copy(input.mFluidInputs)));
        assertFalse(HostedAssemblySelection.mayStart(output, selected, copy(input.mInputs), copy(input.mFluidInputs)));
        assertTrue(HostedAssemblySelection.forPattern(Collections.singletonList(input), pattern(output.mOutput)).isEmpty());
    }

    @Test
    void patternTargetResolvesIdenticalIngredientsWithDifferentProducts() {
        RecipeAssemblyLine first = beam(false);
        RecipeAssemblyLine second = beam(false);
        second.mOutput = beam(true).mOutput;
        List<RecipeAssemblyLine> selected = HostedAssemblySelection.forPattern(Arrays.asList(first, second), pattern(second.mOutput));
        assertEquals(Collections.singletonList(second), selected);
        assertTrue(HostedAssemblySelection.mayStart(second, selected, copy(second.mInputs), copy(second.mFluidInputs)));
    }

    @Test
    void patternMatchesMetadataAndNbtButAllowsMultipliedAmounts() {
        RecipeAssemblyLine right = beam(false);
        right.mOutput = new ItemStack(Items.iron_door, 1, 10504);
        right.mOutput.setTagCompound(new NBTTagCompound());
        right.mOutput.getTagCompound().setString("variant", "target");
        RecipeAssemblyLine wrongMeta = beam(false);
        wrongMeta.mOutput = right.mOutput.copy();
        wrongMeta.mOutput.setItemDamage(10503);
        RecipeAssemblyLine wrongTag = beam(false);
        wrongTag.mOutput = right.mOutput.copy();
        wrongTag.mOutput.getTagCompound().setString("variant", "different");
        ItemStack multiplied = right.mOutput.copy();
        multiplied.stackSize = 64;
        assertEquals(Collections.singletonList(right), HostedAssemblySelection.forPattern(
            Arrays.asList(wrongMeta, wrongTag, right), pattern(multiplied)));
    }

    @Test
    void emptyOrMixedOutputPatternsCannotAuthorizeAnAssemblyRecipe() {
        RecipeAssemblyLine input = beam(false);
        List<RecipeAssemblyLine> recipes = Collections.singletonList(input);
        assertTrue(HostedAssemblySelection.forPattern(recipes, null).isEmpty());
        assertTrue(HostedAssemblySelection.forPattern(recipes, pattern()).isEmpty());
        assertTrue(HostedAssemblySelection.forPattern(recipes, pattern(input.mOutput, beam(true).mOutput)).isEmpty());
    }

    @Test
    void nativePatternSlotExposesTargetAndPlainInventoryUsesFallback() throws Exception {
        RecipeAssemblyLine input = beam(false);
        RecipeAssemblyLine output = beam(true);
        List<RecipeAssemblyLine> recipes = Arrays.asList(input, output);
        // Skip the world/AE inventory constructor; exercise the real PatternSlot accessor used in production.
        java.lang.reflect.Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        PatternSlot<?> slot = (PatternSlot<?>) ((sun.misc.Unsafe) singleton.get(null)).allocateInstance(PatternSlot.class);
        java.lang.reflect.Field details = PatternSlot.class.getDeclaredField("patternDetails");
        details.setAccessible(true);
        details.set(slot, pattern(output.mOutput));
        assertEquals(Collections.singletonList(output), HostedAssemblySelection.forInventory(recipes, slot));
        details.set(slot, null);
        assertTrue(HostedAssemblySelection.forInventory(recipes, slot).isEmpty());
        IDualInputInventory plain = new IDualInputInventory() {
            public boolean isEmpty() { return false; }
            public ItemStack[] getItemInputs() { return copy(input.mInputs); }
            public FluidStack[] getFluidInputs() { return copy(input.mFluidInputs); }
        };
        assertEquals(recipes, HostedAssemblySelection.forInventory(recipes, plain));
    }

    private static ICraftingPatternDetails pattern(ItemStack... outputs) {
        IAEStack<?>[] aeOutputs = Arrays.stream(outputs).map(stack -> (IAEItemStack) java.lang.reflect.Proxy.newProxyInstance(
            HostedAssemblySelectionTest.class.getClassLoader(), new Class<?>[] { IAEItemStack.class },
            (proxy, method, args) -> {
                if (method.getName().equals("getStackSize")) return (long) stack.stackSize;
                if (method.getName().equals("getItemStack")) return stack.copy();
                throw new UnsupportedOperationException(method.getName());
            })).toArray(IAEStack<?>[]::new);
        return (ICraftingPatternDetails) java.lang.reflect.Proxy.newProxyInstance(
            HostedAssemblySelectionTest.class.getClassLoader(), new Class<?>[] { ICraftingPatternDetails.class },
            (proxy, method, args) -> {
                if (method.getName().equals("getAEOutputs")) return aeOutputs;
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static RecipeAssemblyLine beam(boolean output) {
        // The beta-3 beam hatch ingredient topology and quantities, represented with vanilla item identities.
        ItemStack[] inputs = output ? new ItemStack[] {
            new ItemStack(Items.iron_ingot), new ItemStack(Items.redstone, 6), new ItemStack(Items.ender_pearl, 4),
            new ItemStack(Items.bucket, 2), new ItemStack(Items.diamond, 4), new ItemStack(Items.string, 8),
            new ItemStack(Items.stick), new ItemStack(Items.paper, 8)
        } : new ItemStack[] {
            new ItemStack(Items.iron_ingot), new ItemStack(Items.redstone, 2), new ItemStack(Items.ender_pearl, 2),
            new ItemStack(Items.bucket), new ItemStack(Items.stick), new ItemStack(Items.paper, 4)
        };
        return new RecipeAssemblyLine(new ItemStack(Items.book), 1600, 7680, inputs,
            new FluidStack[] { new FluidStack(solder, 9216), new FluidStack(argon, 1000),
                new FluidStack(helium, output ? 6000 : 2000) },
            new ItemStack(output ? Items.iron_door : Items.wooden_door), output ? 4800 : 2400, 30720,
            new ItemStack[inputs.length][]);
    }

    private static Fluid fluid(String name) {
        FluidRegistry.registerFluid(new Fluid(name));
        return FluidRegistry.getFluid(name);
    }

    private static ItemStack[] copy(ItemStack[] items) {
        return Arrays.stream(items).map(ItemStack::copy).toArray(ItemStack[]::new);
    }

    private static FluidStack[] copy(FluidStack[] fluids) {
        return Arrays.stream(fluids).map(FluidStack::copy).toArray(FluidStack[]::new);
    }
}

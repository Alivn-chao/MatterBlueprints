package com.gtnhblueprints.machine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhblueprints.MatterBlueprints;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.FluidEjectionHelper;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipe.RecipeAssemblyLine;
import gregtech.api.util.ItemEjectionHelper;
import gregtech.api.util.ParallelHelper;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot;

/** Executes the selected controller's recipes using only the center's own hatches. */
final class HostedFinalForm {
    private static final BigInteger PERCENT = BigInteger.valueOf(100);
    private static final BigInteger MAX_PARALLEL = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final String STATUS = "matterblueprints.host.final.";

    private final MTEHostedMachineController owner;
    private MTEMultiBlockBase selected;
    private List<RecipeMap<?>> maps = Collections.emptyList();
    private boolean assembly;
    private long nextCheck;
    private String status = STATUS + "no_selector";
    private long completed;
    private BigInteger spent = BigInteger.ZERO;
    private long lastCompleted;
    private BigInteger lastSpent = BigInteger.ZERO;
    private CheckRecipeResult failure;

    HostedFinalForm(MTEHostedMachineController owner) {
        this.owner = owner;
    }

    void reset() {
        selected = null;
        maps = Collections.emptyList();
        assembly = false;
        nextCheck = 0;
        lastCompleted = 0;
        lastSpent = BigInteger.ZERO;
        status = STATUS + "no_selector";
    }

    String status() { return status; }

    long completed() { return lastCompleted; }

    BigInteger spent() { return lastSpent; }

    boolean tick(long tick, boolean enabled, MTEMultiBlockBase machine) {
        if (selected != machine) {
            reset();
            selected = machine;
            assembly = HostedAssemblySupport.isAssembly(machine);
            if (machine != null && !assembly) maps = getRecipeMaps(machine);
        }
        if (!enabled) {
            status = STATUS + "paused";
            return false;
        }
        if (tick < nextCheck) return false;
        completed = 0;
        spent = BigInteger.ZERO;
        failure = CheckRecipeResultRegistry.NO_RECIPE;
        boolean settled = runPass();
        boolean worked = completed > 0;
        if (worked) {
            lastCompleted = completed;
            lastSpent = spent;
        }
        if (settled) {
            if (worked) status = STATUS + "completed";
            owner.setCheckRecipeResult(worked ? CheckRecipeResultRegistry.SUCCESSFUL : failure);
        }
        nextCheck = tick + (worked ? 1 : 5);
        return worked;
    }

    private static List<RecipeMap<?>> getRecipeMaps(MTEMultiBlockBase machine) {
        if (HostedGeneratorSupport.isSupportedClassName(machine.getClass().getName())
            || HostedAntimatterSupport.isAntimatter(machine)) return Collections.emptyList();
        try {
            return machine.getAvailableRecipeMaps().stream().filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        } catch (RuntimeException inaccessible) {
            MatterBlueprints.LOG.debug("Recipe maps for {} unavailable from the controller item", machine.mName,
                inaccessible);
            return Collections.emptyList();
        }
    }

    private boolean runPass() {
        if (selected == null) {
            status = STATUS + "no_selector";
            return true;
        }
        if (!assembly && maps.isEmpty()) {
            status = STATUS + "unsupported";
            return true;
        }
        List<RecipeAssemblyLine> research = assembly ? owner.getHostedAssemblyRecipes() : Collections.emptyList();
        if (assembly && research.isEmpty()) {
            status = "matterblueprints.host.status.assembly_data";
            failure = CheckRecipeResultRegistry.NO_DATA_STICKS;
            return true;
        }
        UUID account = owner.getWirelessAccount();
        if (account == null) {
            status = STATUS + "no_power";
            return true;
        }

        status = STATUS + "no_recipe";
        Map<GTRecipe, Integer> usedParallels = new IdentityHashMap<>();
        Map<RecipeAssemblyLine, HostedAssemblyRecipe> assemblyRecipes = new IdentityHashMap<>();
        // ME pattern inventories are independent. Never use another pattern's ingredients to complete one.
        for (IDualInputHatch hatch : owner.mDualInputHatches) {
            if (hatch == null || !((MetaTileEntity) hatch).isValid()) continue;
            Iterator<? extends IDualInputInventory> inventories = hatch.inventories();
            while (inventories.hasNext()) {
                IDualInputInventory inventory = inventories.next();
                if (inventory.isEmpty()) continue;
                ItemStack[] items = patternItems(hatch.getSharedItems(), inventory.getItemInputs(),
                    ItemList.Circuit_Integrated.getItem());
                Inputs inputs = new Inputs(items, inventory.getFluidInputs());
                for (GTRecipe recipe : candidates(research, inputs, inventory, assemblyRecipes)) {
                    if (!execute(recipe, account, inputs, usedParallels)) return false;
                }
            }
        }

        List<GTRecipe> recipes;
        owner.startRecipeProcessing();
        try {
            recipes = candidates(research, storedInputs(), null, assemblyRecipes);
        } finally {
            owner.endRecipeProcessing();
        }
        for (GTRecipe recipe : recipes) {
            if (!execute(recipe, account, null, usedParallels)) return false;
        }
        return true;
    }

    private Inputs storedInputs() {
        return new Inputs(HostedAssemblySupport.uniqueItems(owner.getStoredInputs(), ItemList.Circuit_Integrated.getItem()),
            HostedAssemblySupport.uniqueFluids(owner.getStoredFluids()));
    }

    static ItemStack[] patternItems(ItemStack[] shared, ItemStack[] pattern, Item integratedCircuit) {
        // GT checks shared inputs first. A configured circuit is stored as a zero-count item.
        List<ItemStack> items = new ArrayList<>(pattern.length + (shared == null ? 0 : shared.length));
        if (shared != null) Collections.addAll(items, shared);
        Collections.addAll(items, pattern);
        return HostedAssemblySupport.uniqueItems(items, integratedCircuit);
    }

    private List<GTRecipe> candidates(List<RecipeAssemblyLine> research,
        Inputs inputs, IDualInputInventory inventory, Map<RecipeAssemblyLine, HostedAssemblyRecipe> assemblyRecipes) {
        if (!assembly) {
            ICraftingPatternDetails pattern = inventory instanceof PatternSlot<?>
                ? ((PatternSlot<?>) inventory).getPatternDetails() : null;
            return findTableRecipes(maps, inputs.items, inputs.fluids, pattern);
        }
        List<RecipeAssemblyLine> authorized = HostedAssemblySelection.forInventory(research, inventory);
        List<GTRecipe> recipes = new ArrayList<>();
        for (RecipeAssemblyLine recipe : authorized) {
            if (HostedAssemblySelection.mayStart(recipe, authorized, inputs.items, inputs.fluids)) {
                recipes.add(assemblyRecipes.computeIfAbsent(recipe, HostedAssemblyRecipe::new));
            }
        }
        return recipes;
    }

    private static List<GTRecipe> findTableRecipes(List<RecipeMap<?>> maps, ItemStack[] items, FluidStack[] fluids,
        ICraftingPatternDetails pattern) {
        return maps.stream()
            .flatMap(map -> map.findRecipeQuery().items(items).fluids(fluids).findAll())
            .filter(recipe -> recipe.mEnabled && !recipe.mFakeRecipe && recipe.mDuration > 0 && recipe.mEUt >= 0)
            .filter(recipe -> pattern == null || matchesPattern(pattern, recipe))
            .distinct().collect(Collectors.toList());
    }

    private static boolean matchesPattern(ICraftingPatternDetails pattern, GTRecipe recipe) {
        IAEStack<?>[] expected = pattern.getAEOutputs();
        if (expected == null || expected.length == 0) return false;
        boolean hasOutput = false;
        // A processing pattern can omit by-products. Each product it promises must come from this recipe.
        for (IAEStack<?> output : expected) {
            if (output == null || output.getStackSize() <= 0) continue;
            hasOutput = true;
            boolean matched = false;
            for (int i = 0; i < recipe.mOutputs.length && !matched; i++) {
                ItemStack item = recipe.mOutputs[i];
                matched = item != null && recipe.getOutputChance(i) > 0 && output.equals(AEItemStack.create(item));
            }
            for (int i = 0; i < recipe.mFluidOutputs.length && !matched; i++) {
                FluidStack fluid = recipe.mFluidOutputs[i];
                matched = fluid != null && recipe.getFluidOutputChance(i) > 0 && output.equals(AEFluidStack.create(fluid));
            }
            if (!matched) return false;
        }
        return hasOutput;
    }

    private boolean execute(GTRecipe recipe, UUID account, Inputs patternInputs, Map<GTRecipe, Integer> usedParallels) {
        int remaining = Integer.MAX_VALUE - usedParallels.getOrDefault(recipe, 0);
        if (remaining <= 0) return true;
        boolean ordinaryInputs = patternInputs == null;
        Batch batch = null;
        boolean charged = false;
        try {
            if (ordinaryInputs) {
                owner.setCheckRecipeResult(CheckRecipeResultRegistry.SUCCESSFUL);
                owner.startRecipeProcessing();
            }
            try {
                Inputs inputs = ordinaryInputs ? storedInputs() : patternInputs;
                batch = plan(recipe, account, inputs, remaining);
                if (batch == null) return true;
                if (batch.debit.signum() > 0) {
                    if (!WirelessNetworkManager.addEUToGlobalEnergyMap(account, batch.debit.negate())) {
                        status = STATUS + "no_power";
                        failure = CheckRecipeResultRegistry.insufficientStartupPower(batch.debit);
                        return true;
                    }
                    charged = true;
                }
                recipe.consumeInput(batch.parallels, inputs.fluids, inputs.items);
            } finally {
                if (ordinaryInputs) owner.endRecipeProcessing();
            }
        } catch (RuntimeException error) {
            if (charged) WirelessNetworkManager.addEUToGlobalEnergyMap(account, batch.debit);
            throw error;
        }
        if (ordinaryInputs && !owner.getCheckRecipeResult().wasSuccessful()) {
            if (charged) WirelessNetworkManager.addEUToGlobalEnergyMap(account, batch.debit);
            status = STATUS + "input_failed";
            return false;
        }
        batch.outputs.commit();
        owner.updateSlots();
        owner.recordFinalFormWork(recipe, batch.parallels);
        owner.markDirty();
        usedParallels.merge(recipe, batch.parallels, Integer::sum);
        completed += batch.parallels;
        spent = spent.add(batch.debit);
        return true;
    }

    private Batch plan(GTRecipe recipe, UUID account, Inputs inputs, int maxParallel) {
        int available = (int) recipe.maxParallelCalculatedByInputs(maxParallel, inputs.fluids, inputs.items);
        if (available <= 0) return null;
        BigInteger undiscounted = BigInteger.valueOf((long) recipe.mEUt * recipe.mDuration);
        BigInteger scaled = undiscounted.multiply(BigInteger.valueOf(owner.getFinalFormPowerPercent()));
        int budget = affordableParallels(WirelessNetworkManager.getUserEU(account), scaled);
        if (budget == 0) {
            status = STATUS + "no_power";
            failure = CheckRecipeResultRegistry.insufficientStartupPower(recipeCost(scaled, 1));
            return null;
        }
        int parallels = Outputs.fit(owner, recipe, Math.min(available, budget));
        Outputs outputs = parallels > 0 ? Outputs.stage(owner, recipe, parallels) : null;
        if (outputs == null) {
            status = "matterblueprints.host.status.output_full";
            failure = CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
            return null;
        }
        return new Batch(parallels, recipeCost(scaled, parallels), outputs);
    }

    static int affordableParallels(BigInteger balance, BigInteger scaledUnitCost) {
        if (scaledUnitCost.signum() == 0) return Integer.MAX_VALUE;
        return balance.max(BigInteger.ZERO).multiply(PERCENT).divide(scaledUnitCost).min(MAX_PARALLEL).intValue();
    }

    static BigInteger recipeCost(BigInteger scaledUnitCost, int parallels) {
        return scaledUnitCost.multiply(BigInteger.valueOf(parallels)).add(PERCENT.subtract(BigInteger.ONE)).divide(PERCENT);
    }

    private static final class Inputs {
        final ItemStack[] items;
        final FluidStack[] fluids;

        Inputs(ItemStack[] items, FluidStack[] fluids) {
            this.items = items;
            this.fluids = fluids == null ? new FluidStack[0] : fluids;
        }
    }

    private static final class Batch {
        final int parallels;
        final BigInteger debit;
        final Outputs outputs;

        Batch(int parallels, BigInteger debit, Outputs outputs) {
            this.parallels = parallels;
            this.debit = debit;
            this.outputs = outputs;
        }
    }

    /** Transactions stage outputs at long totals and touch the actual hatches only after the input commit succeeds. */
    private static final class Outputs {
        final ItemEjectionHelper items;
        final FluidEjectionHelper fluids;

        Outputs(ItemEjectionHelper items, FluidEjectionHelper fluids) {
            this.items = items;
            this.fluids = fluids;
        }

        static int fit(MTEHostedMachineController owner, GTRecipe recipe, int parallels) {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < recipe.mOutputs.length; i++) {
                ItemStack stack = recipe.mOutputs[i];
                if (stack == null || stack.stackSize <= 0 || recipe.getOutputChance(i) <= 0) continue;
                ItemStack maximum = stack.copy();
                maximum.stackSize = worstCase(stack.stackSize, recipe.getOutputChance(i));
                items.add(maximum);
            }
            List<FluidStack> fluids = new ArrayList<>();
            for (int i = 0; i < recipe.mFluidOutputs.length; i++) {
                FluidStack stack = recipe.mFluidOutputs[i];
                if (stack == null || stack.amount <= 0 || recipe.getFluidOutputChance(i) <= 0) continue;
                FluidStack maximum = stack.copy();
                maximum.amount = worstCase(stack.amount, recipe.getFluidOutputChance(i));
                fluids.add(maximum);
            }
            if (!items.isEmpty()) {
                parallels = new ItemEjectionHelper(owner.getOutputBusses(), true, true).ejectItems(items, parallels);
            }
            if (parallels > 0 && !fluids.isEmpty()) {
                parallels = new FluidEjectionHelper(owner.getOutputHatches(recipe.mFluidOutputs), true, true)
                    .ejectFluids(fluids, parallels);
            }
            return parallels;
        }

        private static int worstCase(int amount, int chance) {
            return (int) Math.min(Integer.MAX_VALUE, amount * ((chance + 9999L) / 10000L));
        }

        static Outputs stage(MTEHostedMachineController owner, GTRecipe recipe, int parallels) {
            Map<Long, List<ItemStack>> itemGroups = new LinkedHashMap<>();
            for (int i = 0; i < recipe.mOutputs.length; i++) {
                ItemStack stack = recipe.mOutputs[i];
                if (stack == null || stack.stackSize <= 0) continue;
                long count = outputMultiplier(recipe.getOutputChance(i), parallels);
                if (count > 0) itemGroups.computeIfAbsent(count, key -> new ArrayList<>()).add(stack);
            }
            Map<Long, List<FluidStack>> fluidGroups = new LinkedHashMap<>();
            for (int i = 0; i < recipe.mFluidOutputs.length; i++) {
                FluidStack stack = recipe.mFluidOutputs[i];
                if (stack == null || stack.amount <= 0) continue;
                long count = outputMultiplier(recipe.getFluidOutputChance(i), parallels);
                if (count > 0) fluidGroups.computeIfAbsent(count, key -> new ArrayList<>()).add(stack);
            }
            ItemEjectionHelper items = itemGroups.isEmpty() ? null
                : new ItemEjectionHelper(owner.getOutputBusses(), true, true);
            for (Map.Entry<Long, List<ItemStack>> group : itemGroups.entrySet()) {
                for (long left = group.getKey(); left > 0; left -= Integer.MAX_VALUE) {
                    int count = (int) Math.min(left, Integer.MAX_VALUE);
                    if (items.ejectItems(group.getValue(), count) != count) return null;
                }
            }
            FluidEjectionHelper fluids = fluidGroups.isEmpty() ? null
                : new FluidEjectionHelper(owner.getOutputHatches(recipe.mFluidOutputs), true, true);
            for (Map.Entry<Long, List<FluidStack>> group : fluidGroups.entrySet()) {
                for (long left = group.getKey(); left > 0; left -= Integer.MAX_VALUE) {
                    int count = (int) Math.min(left, Integer.MAX_VALUE);
                    if (fluids.ejectFluids(group.getValue(), count) != count) return null;
                }
            }
            return new Outputs(items, fluids);
        }

        private static long outputMultiplier(int chance, int parallels) {
            if (chance <= 0) return 0;
            return (long) (chance / 10000) * parallels
                + ParallelHelper.calculateIntegralChancedOutputMultiplier(chance % 10000, parallels);
        }

        void commit() {
            if (items != null) items.commit();
            if (fluids != null) fluids.commit();
        }
    }
}

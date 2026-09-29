package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.MatterBlueprints;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.ExoticEnergyInputHelper;
import gregtech.api.util.GTUtility;
import gregtech.api.util.OverclockCalculator;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.multi.drone.MTEHatchDroneDownLink;
import gregtech.common.tileentities.machines.multi.turbines.MTELargeTurbineBase;
import gregtech.common.tileentities.machines.multi.xlturbines.MTEXLTurbineBase;

final class HostedMachineCoordinator {

    private static final int CENTRAL_POWER_CACHE_TICKS = 20;
    private static final int MAINTENANCE_REFRESH_TICKS = 20;
    private static final int MACHINE_PART_CHECK_TICKS = 20;
    private static final int MAX_IDLE_RECIPE_CHECK_TICKS = 100;
    private static final ClassValue<RemoteHooks> REMOTE_HOOKS = new ClassValue<RemoteHooks>() {

        @Override
        protected RemoteHooks computeValue(Class<?> type) {
            return new RemoteHooks(type);
        }
    };
    private static final Field EXOTIC_ENERGY_HATCHES_FIELD = resolveExoticEnergyHatchesField();

    private final MTEHostedMachineController owner;
    private final HostedChemicalPlantSupport chemicalPlants = new HostedChemicalPlantSupport();
    private final HostedFusionSupport.Ignition fusionIgnition = new HostedFusionSupport.Ignition();
    private final HostedProgression progression = new HostedProgression();
    private final HostedYieldAccumulator yieldAccumulator = new HostedYieldAccumulator();
    private final Map<MTEMultiBlockBase, HostedJob> hosted = new IdentityHashMap<MTEMultiBlockBase, HostedJob>();
    private final Set<String> boundMachines = new HashSet<String>();
    private final Set<String> persistedClaims = new HashSet<String>();
    private final Map<String, HostedJob> pendingJobs = new HashMap<String, HostedJob>();
    private final List<ExtraTask> extraTasks = new ArrayList<ExtraTask>();

    static final class ExtraTask {
        final String machine;
        final HostedJob job;
        ExtraTask(String machine, HostedJob job) { this.machine = machine; this.job = job; }
        NBTTagCompound write() {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setString("machine", machine);
            job.writeToNBT(saved);
            return saved;
        }
        static ExtraTask read(NBTTagCompound saved) {
            return new ExtraTask(saved.getString("machine"), HostedJob.readFromNBT(saved));
        }
    }
    private final List<MTEHatch> centralEnergyHatches = new ArrayList<MTEHatch>();
    private final List<MTEHatch> centralDynamoHatches = new ArrayList<MTEHatch>();
    private final Map<MTEHatch, Long> centralDynamoUsedThisTick = new IdentityHashMap<MTEHatch, Long>();
    private MTEMultiBlockBase activeRemote;
    private RemoteRunningContext runningContext;
    private String statusKey = "matterblueprints.host.status.no_match";
    private long energySpentThisTick;
    private boolean consumersAdvancedThisTick;
    private long energyGeneratedThisTick;
    private int generatorsAdvancedThisTick;
    private long centralPowerCapacity;
    private long nextRecipeCheckTick;
    private int idleRecipeCheckDelay;
    private boolean generatorMode;

    HostedMachineCoordinator(MTEHostedMachineController owner) {
        this.owner = owner;
    }

    void refresh() {
        // A periodic rebuild is cheap and picks up a remote structure replacing one of its hatch-list instances.
        runningContext = null;
        ItemStack selector = owner.getControllerSlot();
        if (boundMachines.isEmpty()) {
            releaseAll();
            statusKey = "matterblueprints.host.status.no_bindings";
            return;
        }
        if (selector == null) {
            releaseAll();
            statusKey = "matterblueprints.host.status.no_selector";
            return;
        }

        Set<MTEMultiBlockBase> discovered = Collections
            .newSetFromMap(new IdentityHashMap<MTEMultiBlockBase, Boolean>());
        boolean discoveredGenerator = false;
        for (String machine : boundMachines) {
            MTEMultiBlockBase remote = resolveMachine(machine);
            if (!isEligible(remote, selector)) continue;
            discovered.add(remote);
            discoveredGenerator |= isSupportedGenerator(remote);
            acquire(remote);
        }
        generatorMode = discoveredGenerator;

        Iterator<MTEMultiBlockBase> iterator = hosted.keySet().iterator();
        while (iterator.hasNext()) {
            MTEMultiBlockBase remote = iterator.next();
            if (!discovered.contains(remote)) {
                release(remote, iterator);
            }
        }
        statusKey = hosted.isEmpty() ? "matterblueprints.host.status.no_match" : "matterblueprints.host.status.hosting";
    }

    void tick(long worldTick, boolean allowNewRecipe) {
        energySpentThisTick = 0;
        consumersAdvancedThisTick = false;
        energyGeneratedThisTick = 0;
        generatorsAdvancedThisTick = 0;
        centralDynamoUsedThisTick.clear();
        if ((isGeneratorMode() ? !owner.isWirelessMode() && centralDynamoHatches.isEmpty() : !owner.isWirelessMode() && centralEnergyHatches.isEmpty())
            || worldTick % CENTRAL_POWER_CACHE_TICKS == 0) {
            refreshCentralPower();
        }
        if (isGeneratorMode()) {
            tickGenerators(worldTick, allowNewRecipe);
            deactivateRemotes();
            return;
        }
        for (Map.Entry<MTEMultiBlockBase, HostedJob> entry : hosted.entrySet()) {
            MTEMultiBlockBase remote = entry.getKey();
            HostedJob job = entry.getValue();
            if (!job.isActive()) HostedThermalMachineSupport.idle(remote, job, worldTick, progression.getRuntimeLevel() >= 6);
            if (!job.isActive()) continue;
            if (!isUsable(remote)) {
                if (!job.equivalentAssembly) job.restoreToRemote(remote);
                continue;
            }
            advanceRecipe(remote, job, worldTick, job.energyUsage);
        }
        for (Iterator<ExtraTask> iterator = extraTasks.iterator(); iterator.hasNext();) {
            ExtraTask task = iterator.next();
            if (!task.job.isActive()) { iterator.remove(); continue; }
            MTEMultiBlockBase remote = resolveMachine(task.machine);
            if (remote != null && hosted.containsKey(remote) && isUsable(remote)) {
                advanceRecipe(remote, task.job, worldTick, task.job.energyUsage);
            }
            if (!task.job.isActive()) iterator.remove();
        }
        if (energySpentThisTick > 0 || consumersAdvancedThisTick) {
            progression.recordConsumerTick(energySpentThisTick, consumersAdvancedThisTick);
        }
        refreshActiveRemote();
        MTEMultiBlockBase assemblyPlanner = hosted.isEmpty() ? null : hosted.keySet().iterator().next();
        if (assemblyPlanner != null && HostedAssemblySupport.isAssembly(assemblyPlanner)) {
            if (allowNewRecipe && worldTick >= nextRecipeCheckTick) scheduleAssemblyRecipes(worldTick);
            deactivateRemotes();
            return;
        }
        if (allowNewRecipe && !hosted.isEmpty() && worldTick >= nextRecipeCheckTick) {
            int maximumTasks = Math.min(com.gtnhblueprints.HostedMilestoneConfig.crossRecipeTasks, getTotalParallelCapacity());
            boolean started = false;
            Set<MTEMultiBlockBase> attempted = Collections
                .newSetFromMap(new IdentityHashMap<MTEMultiBlockBase, Boolean>());
            Set<String> activeRecipes = getActiveRecipeKeys();
            int availableTasks = Math.max(0, maximumTasks - getRunningCount());
            int remainingParallel = Math.max(0, getTotalParallelCapacity() - getActiveParallelCount());
            MTEMultiBlockBase planner = chooseRecipeExecutor(attempted);
            List<GTRecipe> recipes = planner == null ? Collections.<GTRecipe>emptyList()
                : findRecipeCandidates(planner, activeRecipes, Math.min(availableTasks, remainingParallel));
            int recipeIndex = 0;
            while (getRunningCount() < maximumTasks && remainingParallel > 0 && recipeIndex < recipes.size()) {
                long availablePower = getAvailableConsumerPowerForNewJob();
                if (availablePower <= 0) break;
                MTEMultiBlockBase representative = chooseRecipeExecutor(attempted);
                if (representative == null) break;
                GTRecipe preferred = recipes.get(recipeIndex++);
                int remainingRecipes = recipes.size() - recipeIndex + 1;
                int laneParallel = Math.max(
                    1,
                    (int) Math.min(
                        Integer.MAX_VALUE,
                        ((long) remainingParallel + remainingRecipes - 1L) / remainingRecipes));
                long lanePower = HostedRecipeScheduling.powerShare(availablePower, remainingRecipes, preferred.mEUt);
                if (startRecipeTask(representative, laneParallel, preferred, lanePower)) {
                    started = true;
                    remainingParallel = Math.max(0, getTotalParallelCapacity() - getActiveParallelCount());
                } else {
                    attempted.add(representative);
                }
            }
            // Machines with custom recipe logic may not expose a normal RecipeMap. Preserve one aggregate fallback.
            if (!started && getRunningCount() == 0 && availableTasks > 0 && remainingParallel > 0 && recipes.isEmpty()) {
                MTEMultiBlockBase representative = chooseRepresentative(attempted);
                long availablePower = getAvailableConsumerPowerForNewJob();
                if (representative != null && availablePower > 0) {
                    started = startRecipe(representative, remainingParallel, null, availablePower);
                }
            }
            refreshActiveRemote();
            scheduleNextRecipeCheck(worldTick, started);
        }
        deactivateRemotes();
    }

    void releaseAll() {
        Iterator<MTEMultiBlockBase> iterator = hosted.keySet().iterator();
        while (iterator.hasNext()) release(iterator.next(), iterator);
        persistedClaims.clear();
        pendingJobs.entrySet().removeIf(entry -> !entry.getValue().equivalentAssembly);
        activeRemote = null;
        runningContext = null;
        centralEnergyHatches.clear();
        centralDynamoHatches.clear();
        centralPowerCapacity = 0;
        generatorMode = false;
    }

    void releaseRemote(MTEMultiBlockBase target) {
        Iterator<MTEMultiBlockBase> iterator = hosted.keySet().iterator();
        while (iterator.hasNext()) {
            MTEMultiBlockBase remote = iterator.next();
            if (remote == target) {
                release(remote, iterator);
                return;
            }
        }
    }

    boolean toggleBinding(MTEMultiBlockBase target) {
        String key = machineKey(target);
        if (boundMachines.remove(key)) {
            if (boundMachines.isEmpty()) fusionIgnition.paid = false;
            releaseRemote(target);
            statusKey = boundMachines.isEmpty() ? "matterblueprints.host.status.no_bindings"
                : "matterblueprints.host.status.no_match";
            return false;
        }
        boundMachines.add(key);
        refresh();
        return true;
    }

    int getBindingCount() {
        return boundMachines.size();
    }

    void writeToNBT(NBTTagCompound tag) {
        fusionIgnition.write(tag);
        NBTTagList extra = new NBTTagList();
        for (ExtraTask task : extraTasks) {
            if (!task.job.isActive()) continue;
            extra.appendTag(task.write());
        }
        tag.setTag("mbHostExtraTasks", extra);
        NBTTagList list = new NBTTagList();
        Map<String, HostedJob> claims = new HashMap<String, HostedJob>(pendingJobs);
        for (String claim : persistedClaims) {
            if (!claims.containsKey(claim)) claims.put(claim, new HostedJob());
        }
        for (Map.Entry<MTEMultiBlockBase, HostedJob> entry : hosted.entrySet()) {
            claims.put(machineKey(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<String, HostedJob> entry : claims.entrySet()) {
            NBTTagCompound claimTag = new NBTTagCompound();
            claimTag.setString("machine", entry.getKey());
            entry.getValue().writeToNBT(claimTag);
            list.appendTag(claimTag);
        }
        tag.setTag("mbHostClaims", list);

        NBTTagList bindings = new NBTTagList();
        for (String machine : boundMachines) bindings.appendTag(new NBTTagString(machine));
        tag.setTag("mbHostBindings", bindings);

        NBTTagCompound progressionTag = new NBTTagCompound();
        progression.writeToNBT(progressionTag);
        tag.setTag("mbHostProgression", progressionTag);
        NBTTagCompound yieldTag = new NBTTagCompound();
        yieldAccumulator.writeToNBT(yieldTag);
        tag.setTag("mbHostYield", yieldTag);
    }

    void readFromNBT(NBTTagCompound tag) {
        fusionIgnition.read(tag);
        extraTasks.clear();
        NBTTagList extra = tag.getTagList("mbHostExtraTasks", 10);
        for (int i = 0; i < extra.tagCount(); i++) {
            NBTTagCompound saved = extra.getCompoundTagAt(i);
            ExtraTask task = ExtraTask.read(saved);
            if (task.job.isActive() && !task.machine.isEmpty()) {
                extraTasks.add(task);
            }
        }
        persistedClaims.clear();
        pendingJobs.clear();
        NBTTagList list = tag.getTagList("mbHostClaims", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound claimTag = list.getCompoundTagAt(i);
            String key = claimTag.getString("machine");
            if (key.isEmpty()) continue;
            persistedClaims.add(key);
            HostedJob job = HostedJob.readFromNBT(claimTag);
            if (job.isActive() || job.thermalGrowth >= 0 || job.plasmaRuntime >= 0) pendingJobs.put(key, job);
        }
        boundMachines.clear();
        NBTTagList bindings = tag.getTagList("mbHostBindings", 8);
        for (int i = 0; i < bindings.tagCount(); i++) {
            String key = bindings.getStringTagAt(i);
            if (!key.isEmpty()) boundMachines.add(key);
        }
        progression.readFromNBT(tag.getCompoundTag("mbHostProgression"));
        yieldAccumulator.readFromNBT(tag.getCompoundTag("mbHostYield"));
    }

    int getHostedCount() {
        return hosted.size();
    }

    int getRunningCount() {
        if (isGeneratorMode()) {
            int count = 0;
            for (HostedJob job : hosted.values()) if (job.isActive()) count++;
            return count;
        }
        int count = 0;
        for (HostedJob job : consumerJobs()) if (job.isActive()) count++;
        return count;
    }

    int getAggregateProgressPercent() {
        HostedJob job = getActiveJob();
        if (job == null || job.maxProgress <= 0) return 0;
        return Math.min(100, job.progress * 100 / job.maxProgress);
    }

    long getEnergySpentThisTick() {
        return isGeneratorMode() ? energyGeneratedThisTick : energySpentThisTick;
    }

    long getCentralPowerCapacity() {
        return centralPowerCapacity;
    }

    List<String> getMachineStatusLines(int maximumLines) {
        return HostedHudLine.render(getMachineHudLines(maximumLines));
    }

    List<String> getProgressionStatusLines() {
        return HostedHudLine.render(getProgressionHudLines());
    }
    List<HostedHudLine> getMachineHudLines(int maximumLines) {
        List<HostedHudLine> result = new ArrayList<HostedHudLine>();
        if (isGeneratorMode()) {
            int running = getRunningCount();
            if (running > 0) {
                result.add(
                    HostedHudLine.text(
                        "matterblueprints.host.info.aggregate_generating",
                        running,
                        HostedNumberFormatter.format(energyGeneratedThisTick),
                        HostedNumberFormatter.format(centralPowerCapacity)));
            } else if (!hosted.isEmpty()) {
                result.add(
                    HostedHudLine.text(
                        "matterblueprints.host.info.aggregate_generator_idle",
                        hosted.size()));
            }
            appendActiveJobLines(result, maximumLines, true);
            return result;
        }
        if (getRunningCount() > 0) {
            appendActiveJobLines(result, maximumLines, false);
        } else if (!hosted.isEmpty()) {
            result.add(
                HostedHudLine.text(
                    "matterblueprints.host.info.aggregate_idle",
                    hosted.size()));
        }
        return result;
    }

    List<HostedHudLine> getProgressionHudLines() {
        List<HostedHudLine> result = new ArrayList<HostedHudLine>(3);
        result.add(
            HostedHudLine.text(
                "matterblueprints.host.progression.levels",
                progression.getRuntimeLevel(),
                progression.getWorkLevel(),
                progression.getEnergyLevel()));
        result.add(
            HostedHudLine.text(
                "matterblueprints.host.hud.time",
                formatDurationTicks(progression.getProductiveTicks()),
                formatDurationTarget(progression.getNextRuntimeTarget())));
        result.add(HostedHudLine.text("matterblueprints.host.hud.work",
                HostedNumberFormatter.format(progression.getWorkUnits()),
                formatLongTarget(progression.getNextWorkTarget())));
        result.add(HostedHudLine.text("matterblueprints.host.hud.energy",
                HostedNumberFormatter.format(progression.getEnergyThroughput()),
                formatEnergyTarget(progression.getNextEnergyTarget())));
        result.add(
            HostedHudLine.text(
                "matterblueprints.host.hud.effects",
                progression.getDurationMultiplier(),
                progression.getPowerMultiplier(), progression.getYieldMultiplier()));
        result.add(HostedHudLine.text("matterblueprints.host.hud.parallel",
                progression.getParallelMultiplier(hosted.size()),
                getHudTaskLimit(),
                HostedHudLine.text(
                    progression.hasVoltageExtension() ? "matterblueprints.host.progression.enabled"
                        : "matterblueprints.host.progression.disabled")));
        appendSpecialMechanics(result);
        return result;
    }

    private void appendSpecialMechanics(List<HostedHudLine> result) {
        if (hosted.isEmpty()) return;
        MTEMultiBlockBase remote = hosted.keySet().iterator().next();
        String prefix = "matterblueprints.host.hud.";
        if (HostedPlasmaForgeSupport.isPlasmaForge(remote)) {
            boolean unlocked = progression.hasPermanentPlasmaFuelDiscount();
            result.add(HostedHudLine.text(prefix + "plasma", state(unlocked),
                com.gtnhblueprints.HostedMilestoneConfig.plasmaFuelHours));
            result.add(HostedHudLine.text(prefix + "plasma_progress", formatDurationTicks(progression.getProductiveTicks()),
                formatDurationTicks(com.gtnhblueprints.HostedMilestoneConfig.plasmaFuelTicks())));
            double reduction = 100D * (1D - HostedPlasmaForgeSupport.fuelMultiplier(progression.getPlasmaForgeRuntime()));
            result.add(HostedHudLine.text(prefix + "plasma_discount", String.format(Locale.ROOT, "%.2f", reduction)));
            result.add(HostedHudLine.text(prefix + "plasma_native"));
        }
        int thermal = HostedThermalMachineSupport.kind(remote);
        if (thermal != 0) {
            result.add(HostedHudLine.text(prefix + "thermal_oc", state(progression.getEnergyLevel() >= 5)));
            if (thermal <= 2) {
                result.add(HostedHudLine.text(prefix + "thermal_unlock", state(progression.getRuntimeLevel() >= 6)));
                float minimum = Float.MAX_VALUE, maximum = 1F;
                for (HostedJob job : hosted.values()) {
                    float growth = HostedThermalMachineSupport.boundedGrowth(job.thermalGrowth, thermal,
                        progression.getRuntimeLevel() >= 6);
                    minimum = Math.min(minimum, growth);
                    maximum = Math.max(maximum, growth);
                }
                result.add(HostedHudLine.text(prefix + (thermal == 1 ? "hearth" : "fridge"),
                    String.format(Locale.ROOT, "%.2f", minimum), String.format(Locale.ROOT, "%.2f", maximum)));
            }
        }
        if (HostedFusionSupport.isFusion(remote)) {
            result.add(HostedHudLine.text(prefix + "fusion"));
            result.add(HostedHudLine.text(prefix + "fusion_capacity",
                HostedNumberFormatter.format(HostedFusionSupport.capacityEU(remote))));
        }
        if (HostedAssemblySupport.isAssembly(remote)) {
            boolean advanced = HostedAssemblySupport.isAdvanced(remote);
            result.add(HostedHudLine.text(prefix + "assembly"));
            result.add(HostedHudLine.text(prefix + "assembly_oc",
                HostedHudLine.text("matterblueprints.host.assembly_oc."
                    + HostedAssemblySupport.overclockMode(advanced, progression.getEnergyLevel()))));
            if (advanced) {
                int fixed = com.gtnhblueprints.HostedMilestoneConfig.advancedAssemblyFixedParallel;
                result.add(HostedHudLine.text(prefix + (fixed > 0 ? "assembly_fixed" : "assembly_inputs"), fixed));
                result.add(HostedHudLine.text(prefix + "assembly_thresholds",
                    com.gtnhblueprints.HostedMilestoneConfig.advancedAssemblyNormalLevel,
                    com.gtnhblueprints.HostedMilestoneConfig.advancedAssemblyPerfectLevel));
            }
        }
        if (HostedChemicalPlantSupport.isChemicalPlant(remote)) result.add(HostedHudLine.text(prefix + "chemical"));
        if (HostedSpaceAssemblerSupport.isSpaceAssembler(remote)) result.add(HostedHudLine.text(prefix + "space"));
        if (isGeneratorMode()) result.add(HostedHudLine.text(prefix + "generator"));
    }

    private static HostedHudLine state(boolean enabled) {
        return HostedHudLine.text("matterblueprints.host.progression." + (enabled ? "enabled" : "disabled"));
    }

    private int getHudTaskLimit() {
        if (hosted.isEmpty()) return 0;
        if (isGeneratorMode()) return hosted.size();
        if (HostedAssemblySupport.isAssembly(hosted.keySet().iterator().next())) {
            return progression.getMaximumTasks(hosted.size());
        }
        return Math.min(com.gtnhblueprints.HostedMilestoneConfig.crossRecipeTasks, getTotalParallelCapacity());
    }

    /** Reference voltage for equivalent amperage; never infer voltage from aggregate EU/t. */
    long getHudVoltage() {
        long voltage = 0;
        if (owner.isWirelessMode()) {
            for (MTEMultiBlockBase remote : hosted.keySet()) {
                if (isGeneratorMode()) {
                    for (MTEHatch hatch : remote.mDynamoHatches) voltage = Math.max(voltage, hatch.maxEUOutput());
                    for (MTEHatch hatch : remote.getExoticDynamoHatches()) voltage = Math.max(voltage, hatch.maxEUOutput());
                } else voltage = Math.max(voltage, remote.getMaxInputVoltage());
            }
        } else {
            for (MTEHatch hatch : isGeneratorMode() ? centralDynamoHatches : centralEnergyHatches) {
                if (hatch != null) voltage = Math.max(voltage, isGeneratorMode() ? hatch.maxEUOutput() : hatch.maxEUInput());
            }
        }
        return voltage;
    }

    private void appendActiveJobLines(List<HostedHudLine> result, int maximumLines, boolean generator) {
        int task = 0;
        for (Map.Entry<MTEMultiBlockBase, HostedJob> entry : hosted.entrySet()) {
            HostedJob job = entry.getValue();
            if (!job.isActive() || result.size() >= maximumLines) continue;
            task++;
            int percent = job.maxProgress <= 0 ? 0 : Math.min(100, job.progress * 100 / job.maxProgress);
            result.add(
                HostedHudLine.text(
                    generator ? "matterblueprints.host.info.generator_task" : "matterblueprints.host.info.task",
                    task,
                    getJobDisplayName(entry.getKey(), job),
                    generator ? "1" : HostedNumberFormatter.format(job.parallels),
                    formatSeconds(job.progress),
                    formatSeconds(job.maxProgress),
                    percent,
                    HostedNumberFormatter.format(job.energyUsage)));
        }
        if (!generator) for (ExtraTask extra : extraTasks) {
            HostedJob job = extra.job;
            if (!job.isActive() || result.size() >= maximumLines) continue;
            MTEMultiBlockBase remote = resolveMachine(extra.machine);
            result.add(HostedHudLine.text("matterblueprints.host.info.task",
                ++task, remote == null ? extra.machine : getJobDisplayName(remote, job),
                HostedNumberFormatter.format(job.parallels), formatSeconds(job.progress), formatSeconds(job.maxProgress),
                Math.min(100, (int) (100L * job.progress / Math.max(1, job.maxProgress))),
                HostedNumberFormatter.format(job.energyUsage)));
        }
    }

    private static Object getJobDisplayName(MTEMultiBlockBase remote, HostedJob job) {
        if (job.outputItems != null) {
            for (ItemStack output : job.outputItems) {
                if (output != null) return output;
            }
        }
        if (job.outputFluids != null) {
            for (FluidStack output : job.outputFluids) {
                if (output != null) return output;
            }
        }
        return remote.getStackForm(1L);
    }

    int getActiveProgress() {
        HostedJob job = getActiveJob();
        return job == null ? 0 : job.progress;
    }

    int getActiveMaxProgress() {
        HostedJob job = getActiveJob();
        return job == null ? 0 : job.maxProgress;
    }

    int getActiveParallels() {
        if (isGeneratorMode()) return getRunningCount();
        HostedJob job = getActiveJob();
        return job == null ? 0 : job.parallels;
    }

    long getActiveEnergyUsage() {
        if (isGeneratorMode()) return energyGeneratedThisTick;
        HostedJob job = getActiveJob();
        return job == null ? 0 : job.energyUsage;
    }

    boolean isGeneratorMode() {
        return generatorMode;
    }

    int getActiveEfficiency() {
        MTEMultiBlockBase remote = getDisplayRemote();
        return remote == null ? 0 : remote.mEfficiency;
    }

    ItemStack[] getActiveOutputItems() {
        HostedJob job = getActiveJob();
        return job == null ? null : job.outputItems;
    }

    FluidStack[] getActiveOutputFluids() {
        HostedJob job = getActiveJob();
        return job == null ? null : job.outputFluids;
    }

    String getStatusKey() {
        return statusKey;
    }

    HostedProgression getProgression() {
        return progression;
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0D);
    }

    private static String formatDurationTicks(long ticks) {
        long seconds = Math.max(0L, ticks) / 20L;
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainder = seconds % 60L;
        if (days > 0) return String.format(Locale.ROOT, "%dd %02d:%02d:%02d", days, hours, minutes, remainder);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }

    private static Object formatDurationTarget(long ticks) {
        return ticks < 0 ? HostedHudLine.text("matterblueprints.host.progression.max")
            : formatDurationTicks(ticks);
    }

    private static Object formatLongTarget(long value) {
        return value < 0 ? HostedHudLine.text("matterblueprints.host.progression.max")
            : HostedNumberFormatter.format(value);
    }

    private static Object formatEnergyTarget(java.math.BigInteger value) {
        return value == null ? HostedHudLine.text("matterblueprints.host.progression.max")
            : HostedNumberFormatter.format(value);
    }

    private HostedJob getActiveJob() {
        if (activeRemote != null) {
            HostedJob job = hosted.get(activeRemote);
            if (job != null && job.isActive()) return job;
        }
        for (HostedJob job : consumerJobs()) if (job.isActive()) return job;
        return null;
    }

    private MTEMultiBlockBase getDisplayRemote() {
        if (activeRemote != null) return activeRemote;
        for (Map.Entry<MTEMultiBlockBase, HostedJob> entry : hosted.entrySet()) {
            if (entry.getValue().isActive()) return entry.getKey();
        }
        return null;
    }

    private boolean isUsable(MTEMultiBlockBase remote) {
        return remote != null && remote.isValid()
            && remote.mMachine
            && remote.getBaseMetaTileEntity() != null
            && (!HostedSpaceAssemblerSupport.isSpaceAssembler(remote)
                || HostedSpaceAssemblerSupport.isConnected(remote));
    }

    private MTEMultiBlockBase chooseRepresentative(Set<MTEMultiBlockBase> excluded) {
        MTEMultiBlockBase result = null;
        long lowestVoltage = Long.MAX_VALUE;
        for (MTEMultiBlockBase remote : hosted.keySet()) {
            HostedJob job = hosted.get(remote);
            if (!isUsable(remote) || job == null || job.isActive() || excluded.contains(remote)) continue;
            long voltage = remote.getMaxInputVoltage();
            if (result == null || voltage < lowestVoltage) {
                result = remote;
                lowestVoltage = voltage;
            }
        }
        return result;
    }

    private List<HostedJob> consumerJobs() {
        List<HostedJob> jobs = new ArrayList<HostedJob>(hosted.values());
        for (ExtraTask task : extraTasks) if (boundMachines.contains(task.machine)) jobs.add(task.job);
        return jobs;
    }

    private List<GTRecipe> findRecipeCandidates(MTEMultiBlockBase remote, Set<String> excluded, int limit) {
        List<GTRecipe> recipes = new ArrayList<GTRecipe>();
        Set<String> keys = new HashSet<String>(excluded);
        owner.startRecipeProcessing();
        try {
            recipes.addAll(RecipePrioritySnapshot.findPreferredRecipes(remote,
                owner.getStoredInputs().toArray(new ItemStack[0]), owner.getStoredFluids().toArray(new FluidStack[0]), keys, limit));
            for (GTRecipe recipe : recipes) keys.add(RecipePrioritySnapshot.recipeKey(recipe));
            for (IDualInputHatch hatch : owner.mDualInputHatches) {
                if (hatch == null || recipes.size() >= limit) continue;
                Iterator<? extends gregtech.common.tileentities.machines.IDualInputInventory> inventories = hatch.inventories();
                while (inventories.hasNext() && recipes.size() < limit) {
                    gregtech.common.tileentities.machines.IDualInputInventory inventory = inventories.next();
                    List<GTRecipe> found = RecipePrioritySnapshot.findPreferredRecipes(remote,
                        inventory.getItemInputs(), inventory.getFluidInputs(), keys, limit - recipes.size());
                    recipes.addAll(found);
                    for (GTRecipe recipe : found) keys.add(RecipePrioritySnapshot.recipeKey(recipe));
                }
            }
            return recipes;
        } finally {
            owner.endRecipeProcessing();
        }
    }

    private MTEMultiBlockBase chooseRecipeExecutor(Set<MTEMultiBlockBase> excluded) {
        MTEMultiBlockBase idle = chooseRepresentative(excluded);
        if (idle != null) return idle;
        for (MTEMultiBlockBase remote : hosted.keySet()) {
            // Stateful native per-tick callbacks require their own physical controller.
            if (isUsable(remote) && !excluded.contains(remote) && !hasCustomRunningTick(remote)
                && HostedThermalMachineSupport.kind(remote) == 0 && !HostedPlasmaForgeSupport.isPlasmaForge(remote)
                && !HostedAssemblySupport.isAssembly(remote)) return remote;
        }
        return null;
    }

    private boolean startRecipeTask(MTEMultiBlockBase remote, int parallel, GTRecipe recipe, long power) {
        HostedJob primary = hosted.get(remote);
        if (!primary.isActive()) return startRecipe(remote, parallel, recipe, power);
        HostedJob lane = new HostedJob();
        hosted.put(remote, lane);
        try {
            if (!startRecipe(remote, parallel, recipe, power)) return false;
            extraTasks.add(new ExtraTask(machineKey(remote), lane));
            return true;
        } finally {
            hosted.put(remote, primary);
        }
    }

    private void refreshActiveRemote() {
        if (activeRemote != null) {
            HostedJob active = hosted.get(activeRemote);
            if (active != null && active.isActive()) return;
        }
        activeRemote = null;
        for (Map.Entry<MTEMultiBlockBase, HostedJob> entry : hosted.entrySet()) {
            if (entry.getValue().isActive()) {
                activeRemote = entry.getKey();
                return;
            }
        }
    }

    private int getTotalParallelCapacity() {
        int base = 0;
        for (MTEMultiBlockBase machine : hosted.keySet()) {
            try (HostedThermalMachineSupport.Scope ignored = HostedThermalMachineSupport.open(machine,
                hosted.get(machine), progression.getRuntimeLevel() >= 6)) {
                base = saturatingParallelAdd(base, HostedFusionSupport.isFusion(machine)
                    ? HostedFusionSupport.capacity(machine) : HostedSpaceAssemblerSupport.getParallelCapacity(machine));
            }
        }
        return progression.applyParallelBonus(Math.max(1, base), hosted.size());
    }

    private int getActiveParallelCount() {
        int total = 0;
        for (HostedJob job : consumerJobs()) {
            if (job.isActive()) total = saturatingParallelAdd(total, Math.max(1, job.reservedParallel));
        }
        return total;
    }

    private Set<String> getActiveRecipeKeys() {
        Set<String> recipes = new HashSet<String>();
        for (HostedJob job : consumerJobs()) {
            if (job.isActive() && job.recipeKey != null && !job.recipeKey.isEmpty()) recipes.add(job.recipeKey);
        }
        return recipes;
    }

    private void deactivateRemotes() {
        for (MTEMultiBlockBase remote : hosted.keySet()) {
            IGregTechTileEntity tile = remote.getBaseMetaTileEntity();
            if (tile == null || !remote.isValid()) continue;
            tile.disableWorking();
            tile.setActive(false);
            remote.mEUt = 0;
            REMOTE_HOOKS.get(remote.getClass()).setLongEUt(remote, 0L);
            tile.setShutDownReason(gregtech.api.util.shutdown.ShutDownReasonRegistry.NONE);
            tile.setShutdownStatus(false);
        }
    }

    private void scheduleNextRecipeCheck(long worldTick, boolean started) {
        int baseDelay = Math.max(1, BlueprintConfig.hostedMachineRecipeCheckIntervalTicks);
        if (started) {
            idleRecipeCheckDelay = baseDelay;
        } else if (idleRecipeCheckDelay <= 0) {
            idleRecipeCheckDelay = Math.min(MAX_IDLE_RECIPE_CHECK_TICKS, baseDelay * 2);
        } else {
            idleRecipeCheckDelay = Math.min(MAX_IDLE_RECIPE_CHECK_TICKS, idleRecipeCheckDelay * 2);
        }
        nextRecipeCheckTick = worldTick + Math.max(baseDelay, idleRecipeCheckDelay);
    }

    private boolean isEligible(MTEMultiBlockBase remote, ItemStack selector) {
        if (remote == null || remote == owner || !remote.isValid() || !remote.mMachine) return false;
        ItemStack remoteController = remote.getStackForm(1);
        if (remoteController == null || !selector.isItemEqual(remoteController)) return false;
        if (isSupportedGenerator(remote)) return getDynamoCapacity(remote) > 0;
        if (HostedSpaceAssemblerSupport.isSpaceAssembler(remote)) {
            return HostedSpaceAssemblerSupport.isConnected(remote) && remote.getMaxInputVoltage() > 0;
        }
        List<MTEHatch> energyHatches = remote.getExoticAndNormalEnergyHatchList();
        return !energyHatches.isEmpty() && ExoticEnergyInputHelper.getTotalEuMulti(energyHatches) > 0;
    }

    private MTEMultiBlockBase resolveMachine(String key) {
        String[] parts = key.split(":", -1);
        if (parts.length != 4) return null;
        try {
            World world = DimensionManager.getWorld(Integer.parseInt(parts[0]));
            if (world == null) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            if (!world.blockExists(x, y, z)) return null;
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!(tile instanceof IGregTechTileEntity)) return null;
            IMetaTileEntity metaTileEntity = ((IGregTechTileEntity) tile).getMetaTileEntity();
            return metaTileEntity instanceof MTEMultiBlockBase ? (MTEMultiBlockBase) metaTileEntity : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void acquire(MTEMultiBlockBase remote) {
        if (hosted.containsKey(remote)) return;
        IGregTechTileEntity tile = remote.getBaseMetaTileEntity();
        if (tile == null || remote.mMaxProgresstime > 0) return;
        String key = machineKey(remote);
        if (!HostedMachineRegistry.claim(remote, owner)) return;
        HostedJob restoredJob = pendingJobs.remove(key);
        hosted.put(remote, restoredJob == null ? new HostedJob() : restoredJob);
        if (restoredJob != null && restoredJob.isActive() && !isSupportedGenerator(remote)) activeRemote = remote;
        persistedClaims.add(key);
        tile.disableWorking();
        tile.setActive(false);
        // Hosting owns energy accounting, so a power-loss marker left immediately before attachment is stale.
        tile.setShutDownReason(gregtech.api.util.shutdown.ShutDownReasonRegistry.NONE);
        tile.setShutdownStatus(false);
        MatterBlueprints.LOG.info("Hosting remote multiblock {} at {},{},{}", remote.mName, tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    private void release(MTEMultiBlockBase remote, Iterator<MTEMultiBlockBase> iterator) {
        HostedMachineRegistry.release(remote, owner);
        chemicalPlants.release(remote);
        HostedJob job = hosted.get(remote);
        HostedPlasmaForgeSupport.release(remote, job);
        HostedThermalMachineSupport.release(remote, job);
        boolean retainAssembly = job != null && job.equivalentAssembly && job.isActive();
        if (retainAssembly) pendingJobs.put(machineKey(remote), job);
        else if (job != null) job.restoreToRemote(remote);
        if (remote == activeRemote) {
            activeRemote = null;
        }
        runningContext = null;
        iterator.remove();
        persistedClaims.remove(machineKey(remote));
        if (!retainAssembly) pendingJobs.remove(machineKey(remote));
        IGregTechTileEntity tile = remote.getBaseMetaTileEntity();
        if (tile != null && remote.isValid()) tile.enableWorking();
    }

    private String machineKey(MTEMultiBlockBase machine) {
        IGregTechTileEntity tile = machine.getBaseMetaTileEntity();
        if (tile == null || tile.getWorld() == null) return "invalid:" + System.identityHashCode(machine);
        return tile.getWorld().provider.dimensionId + ":" + tile.getXCoord() + ":" + tile.getYCoord() + ":" + tile.getZCoord();
    }

    private void scheduleAssemblyRecipes(long worldTick) {
        List<GTRecipe.RecipeAssemblyLine> recipes = owner.getHostedAssemblyRecipes();
        if (recipes.isEmpty()) {
            statusKey = "matterblueprints.host.status.assembly_data";
            scheduleNextRecipeCheck(worldTick, false);
            return;
        }
        int units = progression.applyParallelBonus(hosted.size(), hosted.size());
        for (HostedJob job : hosted.values()) if (job.isActive()) units -= job.assemblyUnits;
        int slots = progression.getMaximumTasks(hosted.size()) - getRunningCount();
        boolean started = false;
        Set<MTEMultiBlockBase> used = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> active = getActiveRecipeKeys();
        for (int i = 0; i < recipes.size() && slots > 0 && units > 0; i++) {
            GTRecipe.RecipeAssemblyLine recipe = recipes.get(i);
            String key = "assembly:" + recipe.getPersistentHash();
            if (active.contains(key)) continue;
            MTEMultiBlockBase remote = chooseRepresentative(used);
            if (remote == null) break;
            long power = getAvailableConsumerPowerForNewJob();
            if (power <= 0) break;
            int laneUnits = Math.max(1, units / Math.min(slots, recipes.size() - i));
            if (!startEquivalentAssembly(remote, recipe, key, laneUnits, power)) continue;
            used.add(remote);
            active.add(key);
            units -= hosted.get(remote).assemblyUnits;
            slots--;
            started = true;
        }
        if (!started && getRunningCount() == 0) statusKey = "matterblueprints.host.status.assembly_no_recipe";
        else if (started) statusKey = "matterblueprints.host.status.hosting";
        refreshActiveRemote();
        scheduleNextRecipeCheck(worldTick, started);
    }

    private boolean startEquivalentAssembly(MTEMultiBlockBase remote, GTRecipe.RecipeAssemblyLine recipe,
        String key, int units, long availablePower) {
        VoltageTierSnapshot voltage = new VoltageTierSnapshot(remote, progression.hasVoltageExtension());
        boolean started = false;
        owner.setCheckRecipeResult(CheckRecipeResultRegistry.SUCCESSFUL);
        try {
            owner.startRecipeProcessing();
            // Crafting-input hatch inventories remain separate, as in GT's native dual-input processing.
            for (IDualInputHatch hatch : owner.mDualInputHatches) {
                Iterator<? extends gregtech.common.tileentities.machines.IDualInputInventory> inventories = hatch.inventories();
                while (inventories.hasNext()) {
                    gregtech.common.tileentities.machines.IDualInputInventory inventory = inventories.next();
                    if (inventory.isEmpty()) continue;
                    List<ItemStack> inputs = new ArrayList<>(java.util.Arrays.asList(inventory.getItemInputs()));
                    ItemStack[] shared = hatch.getSharedItems();
                    if (shared != null) Collections.addAll(inputs, shared);
                    if (commitEquivalentAssembly(remote, recipe, key, units, availablePower,
                        HostedAssemblySupport.uniqueItems(inputs), HostedAssemblySupport.uniqueFluids(
                            java.util.Arrays.asList(inventory.getFluidInputs())))) {
                        started = true;
                        break;
                    }
                }
                if (started) break;
            }
            if (!started) started = commitEquivalentAssembly(remote, recipe, key, units, availablePower,
                HostedAssemblySupport.uniqueItems(owner.getStoredInputs()),
                HostedAssemblySupport.uniqueFluids(owner.getStoredFluids()));
            if (started) owner.updateSlots();
        } finally {
            try {
                owner.endRecipeProcessing();
            } finally {
                voltage.restore();
            }
        }
        if (started && !owner.getCheckRecipeResult().wasSuccessful()) {
            hosted.get(remote).clear();
            return false;
        }
        return started;
    }

    private boolean commitEquivalentAssembly(MTEMultiBlockBase remote, GTRecipe.RecipeAssemblyLine recipe,
        String key, int units, long availablePower, ItemStack[] items, FluidStack[] fluids) {
        HostedAssemblySupport.Prepared prepared = HostedAssemblySupport.prepare(owner, remote, recipe, units,
            availablePower, progression, items, fluids);
        if (prepared == null) return false;
        HostedJob job = hosted.get(remote);
        job.progress = 0;
        job.maxProgress = HostedProcessingSettings.duration(prepared.calculator.getDuration(),
            prepared.helper.getDurationMultiplierDouble());
        job.outputItems = prepared.helper.getItemOutputs();
        job.outputFluids = prepared.helper.getFluidOutputs();
        job.parallels = prepared.helper.getCurrentParallel();
        job.machineCount = hosted.size();
        job.energyUsage = prepared.usage;
        job.recipeKey = key;
        job.equivalentAssembly = true;
        job.assemblyUnits = (int) Math.min(units, ((long) job.parallels + prepared.base - 1) / prepared.base);
        job.reservedParallel = (int) Math.min(Integer.MAX_VALUE, (long) job.assemblyUnits * prepared.base);
        job.recipeLongEUt = -prepared.usage;
        job.recipeEUt = (int) -Math.min(Integer.MAX_VALUE, prepared.usage);
        remote.mEfficiency = Math.max(1000, 10000 - (remote.getIdealStatus() - remote.getRepairStatus()) * 1000);
        applyConsumerBonuses(job, true);
        prepared.plan.consume(items, fluids);
        job.hideProgressFromRemote(remote);
        return true;
    }

    private boolean startRecipe(MTEMultiBlockBase remote, int parallelCapacity, GTRecipe preferred,
        long availablePower) {
        if (HostedFusionSupport.isFusion(remote)) {
            if (!HostedFusionSupport.matches(remote, hosted.keySet())) {
                statusKey = "matterblueprints.host.status.fusion_configuration";
                return false;
            }
            try (HostedFusionSupport.Scope ignored = HostedFusionSupport.open(remote, fusionIgnition,
                this::drainFusionStartupEnergy, perMachine -> progression.applyParallelBonus(
                    HostedFusionSupport.parallelProduct(perMachine, hosted.size()), hosted.size()))) {
                return startAggregatedRecipe(remote, parallelCapacity, preferred, availablePower);
            }
        }
        if (HostedThermalMachineSupport.kind(remote) != 0) {
            if (!HostedThermalMachineSupport.matches(remote, hosted.keySet())) {
                statusKey = "matterblueprints.host.status.thermal_configuration";
                return false;
            }
            try (HostedThermalMachineSupport.Scope ignored = HostedThermalMachineSupport.open(remote, hosted.get(remote),
                progression.getRuntimeLevel() >= 6, progression.getConsumerDurationMultiplier())) {
                return startAggregatedRecipe(remote, parallelCapacity, preferred, availablePower);
            }
        }
        if (HostedPlasmaForgeSupport.isPlasmaForge(remote)) {
            if (!HostedPlasmaForgeSupport.matches(remote, hosted.keySet())) {
                statusKey = "matterblueprints.host.status.plasma_configuration";
                return false;
            }
            try (HostedPlasmaForgeSupport.Scope ignored = HostedPlasmaForgeSupport.open(remote, hosted.get(remote),
                progression.getPlasmaForgeRuntime())) {
                // Native matching understands discounted fuel and alternate catalyst selection.
                return startAggregatedRecipe(remote, parallelCapacity, null, availablePower);
            }
        }
        if (!HostedChemicalPlantSupport.isChemicalPlant(remote)) {
            return startAggregatedRecipe(remote, parallelCapacity, preferred, availablePower);
        }
        try {
            if (!HostedChemicalPlantSupport.hasMatchingConfiguration(remote, hosted.keySet())) {
                statusKey = "matterblueprints.host.status.chemical_tiers";
                return false;
            }
            try (HostedChemicalPlantSupport.Scope ignored = chemicalPlants.open(remote, owner.getHostedCatalysts())) {
                boolean started = startAggregatedRecipe(remote, parallelCapacity, preferred, availablePower);
                statusKey = started ? "matterblueprints.host.status.hosting"
                    : "matterblueprints.host.status.chemical_no_recipe";
                return started;
            }
        } catch (RuntimeException | LinkageError error) {
            MatterBlueprints.LOG.error("Could not prepare central chemical plant catalysts for {}", remote.mName, error);
            statusKey = "matterblueprints.host.status.chemical_error";
            return false;
        }
    }

    private boolean startAggregatedRecipe(MTEMultiBlockBase remote, int parallelCapacity, GTRecipe preferred,
        long availablePower) {
        HatchSnapshot snapshot = new HatchSnapshot(remote);
        snapshot.useOwnerIO(owner, remote);
        VoltageTierSnapshot voltageSnapshot = new VoltageTierSnapshot(remote, progression.hasVoltageExtension());
        int admittedParallel = getPowerLimitedParallel(remote, preferred, parallelCapacity, availablePower);
        if (admittedParallel <= 0) {
            voltageSnapshot.restore();
            snapshot.restore(remote);
            statusKey = owner.isWirelessMode() ? "matterblueprints.wireless.no_power"
                : "matterblueprints.host.status.no_central_power";
            return false;
        }
        AggregateCapacitySnapshot capacitySnapshot = new AggregateCapacitySnapshot(
            remote,
            hosted.keySet(),
            admittedParallel,
            progression.getNativePowerBudget(availablePower,
                10000 - (remote.getIdealStatus() - remote.getRepairStatus()) * 1000),
            HostedThermalMachineSupport.kind(remote) != 0 && progression.getEnergyLevel() >= 5,
            progression.getConsumerDurationMultiplier(), owner.isBatchModeEnabled());
        RecipePrioritySnapshot prioritySnapshot = new RecipePrioritySnapshot(remote, preferred);
        boolean restoreBatchMode = remote.supportsBatchMode();
        boolean previousBatchMode = remote.isBatchModeEnabled();
        // Batch recipes consume additional inputs in exchange for a longer job at the same EU/t.
        if (restoreBatchMode) remote.setBatchMode(owner.isBatchModeEnabled());
        boolean successful = false;
        try {
            remote.startRecipeProcessing();
            CheckRecipeResult result = remote.checkProcessing();
            remote.setCheckRecipeResult(result);
            successful = result.wasSuccessful();
        } catch (RuntimeException error) {
            MatterBlueprints.LOG.error("Hosted recipe check failed for {}", remote.mName, error);
            remote.stopMachine();
        } finally {
            try {
                remote.endRecipeProcessing();
            } finally {
                if (restoreBatchMode) remote.setBatchMode(previousBatchMode);
                capacitySnapshot.restore(remote);
                voltageSnapshot.restore();
                snapshot.restore(remote);
            }
        }
        if (successful) {
            HostedJob job = hosted.get(remote);
            if (job != null) {
                long completed = getCompletedRecipeCount(remote);
                job.captureFromRemote(remote, hosted.size(), completed, getActualEnergyUsage(remote));
                job.reservedParallel = Math.min(admittedParallel, job.parallels);
                job.recipeKey = prioritySnapshot.getSelectedRecipeKey(remote);
                applyConsumerBonuses(job, capacitySnapshot.processingSettings != null);
                MatterBlueprints.LOG.debug(
                    "Started hosted recipe on {}: parallel={}, EU/t={}, duration={} ticks, power budget={} EU/t",
                    remote.mName,
                    job.parallels,
                    job.energyUsage,
                    job.maxProgress,
                    availablePower);
                activeRemote = remote;
                return true;
            }
        }
        return false;
    }

    /** Calculates the amount of parallel work that can be powered before the native recipe check consumes inputs. */
    private int getPowerLimitedParallel(MTEMultiBlockBase remote, GTRecipe recipe, int requested, long availablePower) {
        if (requested <= 0 || availablePower <= 0) return 0;
        if (availablePower == Long.MAX_VALUE) return requested;
        // Native ProcessingLogic applies the budget after machine-specific setup and before consuming inputs.
        try {
            Field field = MTEMultiBlockBase.class.getDeclaredField("processingLogic");
            field.setAccessible(true);
            if (field.get(remote) instanceof ProcessingLogic) return requested;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect hosted processing logic", error);
        }
        long perParallel = estimateRecipePower(remote, recipe);
        if (perParallel <= 0 || perParallel > availablePower) return 0;
        return (int) Math.min((long) requested, Math.max(1L, availablePower / perParallel));
    }

    private long estimateRecipePower(MTEMultiBlockBase remote, GTRecipe recipe) {
        long machineVoltage = Math.max(1L, remote.getMaxInputVoltage());
        if (recipe == null || recipe.mEUt <= 0 || recipe.mDuration <= 0) return machineVoltage;
        try {
            long consumption = new OverclockCalculator().setRecipeEUt(recipe.mEUt)
                .setDuration(recipe.mDuration)
                .setEUt(machineVoltage)
                .setAmperage(1)
                .setParallel(1)
                .setAmperageOC(true)
                .calculate()
                .getConsumption();
            return progression.applyConsumerPower(Math.max(1L, consumption));
        } catch (RuntimeException error) {
            MatterBlueprints.LOG.debug("Could not estimate hosted recipe power for {}; using its machine voltage",
                remote.mName, error);
            return progression.applyConsumerPower(machineVoltage);
        }
    }

    /**
     * Generator controllers retain one job per physical machine because rotor wear, warm-up, coolant and fuel state
     * belong to that controller. The host only aggregates their per-tick output and display state, avoiding a second
     * full multiblock tick for every remote machine.
     */
    private void tickGenerators(long worldTick, boolean allowNewRecipe) {
        if (!owner.isWirelessMode() && (centralDynamoHatches.isEmpty() || centralPowerCapacity <= 0)) {
            statusKey = "matterblueprints.host.status.no_central_dynamo";
            return;
        }
        if (owner.isWirelessMode() && !owner.canGenerateWirelessly()) {
            statusKey = "matterblueprints.wireless.invalid_generation_owner";
            return;
        }
        statusKey = owner.isWirelessMode() ? "matterblueprints.wireless.generating"
            : "matterblueprints.host.status.hosting_generators";

        for (Map.Entry<MTEMultiBlockBase, HostedJob> entry : hosted.entrySet()) {
            MTEMultiBlockBase remote = entry.getKey();
            HostedJob job = entry.getValue();
            if (!job.isActive()) continue;
            if (!isUsable(remote)) {
                job.restoreToRemote(remote);
                continue;
            }
            advanceGenerator(remote, job, worldTick);
        }

        if (allowNewRecipe) {
            List<MTEMultiBlockBase> idleTurbines = idleTurbines(worldTick);
            Map<MTEMultiBlockBase, HostedTurbineFlowScheduler.FlowPlan> turbinePlans = idleTurbines.isEmpty()
                ? Collections.<MTEMultiBlockBase, HostedTurbineFlowScheduler.FlowPlan>emptyMap()
                : HostedTurbineFlowScheduler.plan(owner, idleTurbines);
            for (Map.Entry<MTEMultiBlockBase, HostedJob> entry : hosted.entrySet()) {
                HostedJob job = entry.getValue();
                if (job.isActive() || worldTick < job.nextRecipeCheckTick) continue;
                MTEMultiBlockBase remote = entry.getKey();
                HostedTurbineFlowScheduler.FlowPlan plan = turbinePlans.get(remote);
                if (HostedTurbineFlowScheduler.isTurbine(remote) && plan == null) {
                    // Avoid polling every empty turbine every tick while still responding within one second to refills.
                    job.nextRecipeCheckTick = worldTick + 20L;
                    job.idleRecipeCheckDelay = 0;
                    continue;
                }
                boolean started = startGeneratorRecipe(remote, job, plan);
                job.scheduleNextRecipeCheck(worldTick, started);
            }
        }
        if (energyGeneratedThisTick > 0) {
            progression.recordGeneratorTick(energyGeneratedThisTick, generatorsAdvancedThisTick);
        }
    }

    private List<MTEMultiBlockBase> idleTurbines(long worldTick) {
        List<MTEMultiBlockBase> turbines = new ArrayList<MTEMultiBlockBase>();
        for (Map.Entry<MTEMultiBlockBase, HostedJob> entry : hosted.entrySet()) {
            HostedJob job = entry.getValue();
            if (!job.isActive() && worldTick >= job.nextRecipeCheckTick
                && HostedTurbineFlowScheduler.isTurbine(entry.getKey())) {
                tryRefillRemoteTurbines(entry.getKey());
                turbines.add(entry.getKey());
            }
        }
        return turbines;
    }

    private boolean startGeneratorRecipe(MTEMultiBlockBase remote, HostedJob job,
        HostedTurbineFlowScheduler.FlowPlan plan) {
        if (!isUsable(remote) || !isSupportedGenerator(remote)) return false;
        tryRefillRemoteTurbines(remote);
        HatchSnapshot snapshot = new HatchSnapshot(remote);
        snapshot.useOwnerIO(owner, remote);
        HostedTurbineFlowScheduler.FluidQuota quota = null;
        boolean successful = false;
        try {
            remote.startRecipeProcessing();
            quota = HostedTurbineFlowScheduler.restrict(remote, plan);
            CheckRecipeResult result = remote.checkProcessing();
            remote.setCheckRecipeResult(result);
            successful = result.wasSuccessful();
        } catch (RuntimeException error) {
            MatterBlueprints.LOG.error("Hosted generator recipe check failed for {}", remote.mName, error);
            remote.stopMachine();
        } finally {
            try {
                if (quota != null) quota.restore();
            } finally {
                try {
                    remote.endRecipeProcessing();
                } finally {
                    snapshot.restore(remote);
                }
            }
        }
        if (!successful || remote.mMaxProgresstime <= 0) return false;
        job.captureFromRemote(remote, 1, 1, REMOTE_HOOKS.get(remote.getClass()).getGeneratedPower(remote));
        job.maxProgress = progression.applyGeneratorDuration(job.maxProgress, hosted.size());
        return true;
    }

    private void advanceGenerator(MTEMultiBlockBase remote, HostedJob job, long worldTick) {
        long expectedOutput = REMOTE_HOOKS.get(remote.getClass()).getGeneratedPower(remote);
        long reservedCapacity = expectedOutput > 0 ? expectedOutput : getDynamoCapacity(remote);
        if (reservedCapacity <= 0
            || saturatingAdd(energyGeneratedThisTick, reservedCapacity) > centralPowerCapacity
            || getCentralDynamoFreeCapacity() < reservedCapacity) {
            statusKey = "matterblueprints.host.status.dynamo_capacity";
            return;
        }

        job.exposeProgressToRemote(remote);
        boolean continued = false;
        HatchSnapshot ioSnapshot = new HatchSnapshot(remote);
        DynamoEnergySnapshot dynamoSnapshot = new DynamoEnergySnapshot(remote);
        try {
            if (worldTick % MAINTENANCE_REFRESH_TICKS == 0) remote.checkMaintenance();
            tryRefillRemoteTurbines(remote);
            if (!checkMachinePartAndMaintenance(remote, worldTick)) return;

            ioSnapshot.useOwnerIO(owner, remote);
            if (!remote.onRunningTick(remote.getControllerSlot())) return;
            if (remote.mMaxProgresstime <= 0) return;

            long generated = REMOTE_HOOKS.get(remote.getClass()).getGeneratedPower(remote);
            if (saturatingAdd(energyGeneratedThisTick, generated) > centralPowerCapacity
                || getCentralDynamoFreeCapacity() < generated) {
                statusKey = "matterblueprints.host.status.dynamo_capacity";
                return;
            }
            long accepted = injectCentralEnergy(generated);
            if (accepted <= 0) {
                statusKey = "matterblueprints.host.status.dynamo_capacity";
                return;
            }
            energyGeneratedThisTick = saturatingAdd(energyGeneratedThisTick, accepted);
            generatorsAdvancedThisTick++;
            job.energyUsage = accepted;

            if (!remote.polluteEnvironment(remote.getPollutionPerTick(remote.getControllerSlot()))) {
                remote.stopMachine();
                return;
            }
            invokeIncrementProgressTime(remote);
            job.progress = remote.mProgresstime;
            continued = true;
        } finally {
            dynamoSnapshot.restore();
            ioSnapshot.restore(remote);
            boolean remoteStopped = remote.mMaxProgresstime <= 0;
            job.hideProgressFromRemote(remote);
            if (!continued && remoteStopped) job.clear();
        }
        if (!job.isActive() || job.progress < job.maxProgress) return;
        finishGeneratorRecipe(remote, job, worldTick);
    }

    private void finishGeneratorRecipe(MTEMultiBlockBase remote, HostedJob job, long worldTick) {
        recordDroneProduction(remote, job);
        boolean itemsAccepted = job.outputItems == null || owner.addItemOutputs(job.outputItems);
        boolean fluidsAccepted = addFluidOutputs(job.outputFluids);
        HatchSnapshot snapshot = new HatchSnapshot(remote);
        snapshot.useOwnerIO(owner, remote);
        job.exposeProgressToRemote(remote);
        try {
            invokeOutputAfterRecipe(remote);
        } finally {
            job.hideProgressFromRemote(remote);
            snapshot.restore(remote);
        }
        remote.mEfficiency = Math.max(
            0,
            Math.min(
                remote.mEfficiency + remote.mEfficiencyIncrease,
                remote.getMaxEfficiency(remote.getControllerSlot())
                    - ((remote.getIdealStatus() - remote.getRepairStatus()) * 1000)));
        remote.mEfficiencyIncrease = 0;
        remote.recipesDone++;
        remote.setLastWorkingTick(remote.getTotalRunTime());
        job.clear();
        job.nextRecipeCheckTick = worldTick;
        job.idleRecipeCheckDelay = 0;
        if (!itemsAccepted || !fluidsAccepted) statusKey = "matterblueprints.host.status.output_full";
    }

    private void advanceRecipe(MTEMultiBlockBase remote, HostedJob job, long worldTick, long allocatedPower) {
        if (job.progress >= job.maxProgress) {
            finishRecipe(remote, job, worldTick);
            return;
        }
        job.exposeProgressToRemote(remote);
        boolean continued = false;
        try {
            // Vanilla GT synchronizes the six maintenance flags from the hatch before runMachine().
            // Hosted machines do not execute their own runMachine(), so this must happen here as well.
            // The disabled remote still performs its own base maintenance synchronization. Refreshing here once per
            // second covers forks that skip it without repeating six hatch reads for every aggregate tick.
            if (worldTick % MAINTENANCE_REFRESH_TICKS == 0) remote.checkMaintenance();
            if (!checkMachinePartAndMaintenance(remote, worldTick)) return;
            long energyUsage = Math.max(1L, job.energyUsage);
            long supplied = owner.isWirelessMode()
                ? (drainCentralEnergy(energyUsage) ? energyUsage : 0L)
                : drainCentralEnergyUpTo(Math.min(energyUsage, allocatedPower));
            job.energyCredit = Math.min(energyUsage, saturatingAdd(job.energyCredit, supplied));
            if (job.energyCredit < energyUsage) {
                statusKey = owner.isWirelessMode() ? "matterblueprints.wireless.no_power"
                    : "matterblueprints.host.status.no_central_power";
                return;
            }
            job.energyCredit -= energyUsage;
            if (owner.isWirelessMode()) statusKey = "matterblueprints.wireless.running";
            if (!job.equivalentAssembly && hasCustomRunningTick(remote)) {
                RemoteRunningContext context = getRunningContext(remote);
                HatchSnapshot thermalIO = HostedThermalMachineSupport.kind(remote) != 0 ? new HatchSnapshot(remote) : null;
                if (thermalIO != null) thermalIO.useOwnerIO(owner, remote);
                try (HostedThermalMachineSupport.Scope ignored = HostedThermalMachineSupport.open(remote, job,
                    progression.getRuntimeLevel() >= 6)) {
                    context.stage(remote, energyUsage);
                    if (thermalIO != null) remote.startRecipeProcessing();
                    try {
                        if (!remote.onRunningTick(remote.getControllerSlot())) return;
                    } finally {
                        if (thermalIO != null) remote.endRecipeProcessing();
                    }
                } finally {
                    context.restore(remote);
                    if (thermalIO != null) thermalIO.restore(remote);
                }
            }
            if (!remote.polluteEnvironment(remote.getPollutionPerTick(remote.getControllerSlot()))) {
                remote.stopMachine();
                return;
            }
            int previousProgress = job.progress;
            if (job.equivalentAssembly) job.progress++;
            else {
                invokeIncrementProgressTime(remote);
                job.progress = remote.mProgresstime;
            }
            if (job.progress > previousProgress) {
                consumersAdvancedThisTick = true;
            }
            continued = true;
        } finally {
            boolean remoteStopped = remote.mMaxProgresstime <= 0;
            job.hideProgressFromRemote(remote);
            if (!continued && remoteStopped) job.clear();
        }
        if (!job.isActive() || job.progress < job.maxProgress) return;
        finishRecipe(remote, job, worldTick);
    }

    private boolean checkMachinePartAndMaintenance(MTEMultiBlockBase remote, long worldTick) {
        if (remote.mRuntime >= 1000 || worldTick % MACHINE_PART_CHECK_TICKS == 0) {
            return remote.doRandomMaintenanceDamage();
        }
        remote.mRuntime++;
        return true;
    }

    /**
     * Ordinary large turbines do not refill their controller slot themselves. Pull a spare from that physical
     * turbine's own input busses before central I/O is staged. XL turbines keep GT's native 12-slot refill routine;
     * invoking it here ensures it also sees the remote busses while the controller is disabled by hosting.
     */
    private void tryRefillRemoteTurbines(MTEMultiBlockBase remote) {
        if (!HostedTurbineFlowScheduler.isTurbine(remote)) return;
        if (remote instanceof MTEXLTurbineBase) {
            if (remote.getMaxParallelRecipes() < 12) REMOTE_HOOKS.get(remote.getClass()).invokeTurbineRefill(remote);
            return;
        }
        if (!(remote instanceof MTELargeTurbineBase) || remote.isCorrectMachinePart(remote.getControllerSlot())) return;

        ItemStack replacement = null;
        CheckRecipeResult previousResult = remote.getCheckRecipeResult();
        boolean extractionSuccessful = false;
        try {
            remote.setCheckRecipeResult(CheckRecipeResultRegistry.SUCCESSFUL);
            remote.startRecipeProcessing();
            try {
                for (ItemStack candidate : remote.getStoredInputs()) {
                    if (!remote.isCorrectMachinePart(candidate)) continue;
                    ItemStack oneRotor = GTUtility.copyAmount(1, candidate);
                    if (remote.depleteInput(oneRotor)) {
                        replacement = oneRotor;
                        break;
                    }
                }
            } finally {
                remote.endRecipeProcessing();
            }
            extractionSuccessful = remote.getCheckRecipeResult().wasSuccessful();
        } finally {
            remote.setCheckRecipeResult(previousResult);
        }
        if (replacement == null || !extractionSuccessful) return;
        remote.setInventorySlotContents(1, replacement);
        remote.updateSlots();
        remote.markDirty();
    }

    private RemoteRunningContext getRunningContext(MTEMultiBlockBase remote) {
        if (runningContext == null || runningContext.representative != remote) {
            runningContext = new RemoteRunningContext(remote, hosted.keySet());
        }
        return runningContext;
    }

    private void finishRecipe(MTEMultiBlockBase remote, HostedJob job, long worldTick) {
        if (!flushFusionOutputs(job)) {
            statusKey = "matterblueprints.host.status.output_full";
            return;
        }
        recordDroneProduction(remote, job);
        HatchSnapshot snapshot = new HatchSnapshot(remote);
        snapshot.useOwnerIO(owner, remote);
        job.exposeProgressToRemote(remote);
        try {
            if (!job.equivalentAssembly) invokeOutputAfterRecipe(remote);
        } finally {
            job.hideProgressFromRemote(remote);
            snapshot.restore(remote);
        }
        remote.mEfficiency = Math.max(
            0,
            Math.min(
                remote.mEfficiency + remote.mEfficiencyIncrease,
                remote.getMaxEfficiency(remote.getControllerSlot())
                    - ((remote.getIdealStatus() - remote.getRepairStatus()) * 1000)));
        remote.mEfficiencyIncrease = 0;
        remote.recipesDone += job.parallels;
        progression.recordCompletedRecipes(job.parallels);
        remote.setLastWorkingTick(remote.getTotalRunTime());
        job.clear();
        activeRemote = null;
        nextRecipeCheckTick = Math.min(nextRecipeCheckTick, worldTick);
        idleRecipeCheckDelay = 0;
    }

    private void applyConsumerBonuses(HostedJob job, boolean speedApplied) {
        if (!speedApplied) job.maxProgress = progression.applyConsumerDuration(job.maxProgress);
        job.energyUsage = progression.applyConsumerPower(job.energyUsage);
        yieldAccumulator.apply(job.outputItems, job.outputFluids, progression.getYieldQuarterBonus());
    }

    private void recordDroneProduction(MTEMultiBlockBase remote, HostedJob job) {
        if (remote.mMaintenanceHatches.isEmpty()) return;
        if (!(remote.mMaintenanceHatches.get(0) instanceof MTEHatchDroneDownLink)) return;
        MTEHatchDroneDownLink downLink = (MTEHatchDroneDownLink) remote.mMaintenanceHatches.get(0);
        if (downLink.getCentre() == null || !downLink.getCentre().productionDataRecorder.isActive()) return;
        downLink.getCentre().productionDataRecorder
            .addRecord(((long) job.maxProgress) * remote.mEUt, job.outputItems, job.outputFluids);
    }

    private boolean addFluidOutputs(FluidStack[] outputs) {
        if (outputs == null) return true;
        boolean accepted = true;
        for (FluidStack output : outputs) {
            if (output != null && output.amount > 0) {
                FluidStack remaining = output.copy();
                owner.addOutputPartial(remaining);
                if (remaining.amount > 0) accepted = false;
            }
        }
        return accepted;
    }

    private boolean flushFusionOutputs(HostedJob job) {
        boolean accepted = true;
        if (job.outputItems != null) for (ItemStack output : job.outputItems) {
            if (output != null && output.stackSize > 0 && !owner.addOutputAtomic(output)) accepted = false;
        }
        return HostedFusionSupport.flushFluids(job.outputFluids,
            output -> owner.addOutputPartial(output, owner.getOutputHatches(new FluidStack[] { output }), true)) && accepted;
    }

    private void invokeOutputAfterRecipe(MTEMultiBlockBase remote) {
        REMOTE_HOOKS.get(remote.getClass()).invokeOutputAfterRecipe(remote);
    }

    private void invokeIncrementProgressTime(MTEMultiBlockBase remote) {
        REMOTE_HOOKS.get(remote.getClass()).incrementProgress(remote);
    }

    private boolean hasCustomRunningTick(MTEMultiBlockBase remote) {
        return REMOTE_HOOKS.get(remote.getClass()).hasCustomRunningTick;
    }

    private long getActualEnergyUsage(MTEMultiBlockBase remote) {
        return REMOTE_HOOKS.get(remote.getClass()).getActualEnergyUsage(remote);
    }

    private void refreshCentralPower() {
        centralEnergyHatches.clear();
        centralDynamoHatches.clear();
        if (isGeneratorMode()) {
            centralDynamoHatches.addAll(owner.mDynamoHatches);
            centralDynamoHatches.addAll(owner.getExoticDynamoHatches());
            centralPowerCapacity = getDynamoCapacity(centralDynamoHatches);
            if (owner.isWirelessMode()) {
                centralPowerCapacity = 0;
                for (MTEMultiBlockBase remote : hosted.keySet()) {
                    centralPowerCapacity = saturatingAdd(centralPowerCapacity, getDynamoCapacity(remote));
                }
            }
        } else {
            centralEnergyHatches.addAll(owner.getExoticAndNormalEnergyHatchList());
            centralPowerCapacity = ExoticEnergyInputHelper.getTotalEuMulti(centralEnergyHatches);
        }
    }

    /**
     * Returns the per-tick power that can still be committed to a newly admitted consumer job. Existing hosted jobs
     * reserve their full EU/t even when they happened to stall during this tick. Requiring a currently charged central
     * buffer also prevents server startup from consuming recipe inputs before the energy network has initialized.
     */
    private long getAvailableConsumerPowerForNewJob() {
        if (owner.isWirelessMode()) return Long.MAX_VALUE;
        long reserved = 0;
        for (HostedJob job : consumerJobs()) {
            if (job.isActive()) reserved = saturatingAdd(reserved, job.energyUsage);
        }
        long throughput = Math.max(0L, centralPowerCapacity - reserved);
        if (throughput <= 0) return 0;
        long stored = 0;
        Set<MTEHatch> seen = Collections.newSetFromMap(new IdentityHashMap<MTEHatch, Boolean>());
        for (MTEHatch hatch : centralEnergyHatches) {
            if (hatch == null || hatch.getBaseMetaTileEntity() == null || !seen.add(hatch)) continue;
            stored = saturatingAdd(stored, hatch.getBaseMetaTileEntity().getStoredEU());
        }
        return Math.min(throughput, stored);
    }

    private long injectCentralEnergy(long amount) {
        if (owner.isWirelessMode()) return owner.generateWirelessEnergy(amount) ? amount : 0L;
        long remaining = Math.max(0L, amount);
        for (MTEHatch hatch : centralDynamoHatches) {
            if (remaining <= 0) break;
            if (hatch == null || hatch.getBaseMetaTileEntity() == null) continue;
            long hatchLimit = saturatingMultiply(hatch.maxEUOutput(), hatch.maxAmperesOut());
            long used = centralDynamoUsedThisTick.getOrDefault(hatch, 0L);
            long availableThroughput = Math.max(0L, hatchLimit - used);
            long assigned = Math.min(remaining, availableThroughput);
            long stored = hatch.getEUVar();
            long free = Math.max(0L, hatch.maxEUStore() - stored);
            long inserted = Math.min(assigned, free);
            hatch.setEUVar(saturatingAdd(stored, inserted));
            if (inserted > 0) centralDynamoUsedThisTick.put(hatch, saturatingAdd(used, inserted));
            remaining -= inserted;
        }
        return Math.max(0L, amount - remaining);
    }

    private long getCentralDynamoFreeCapacity() {
        if (owner.isWirelessMode()) return owner.canGenerateWirelessly() ? Long.MAX_VALUE : 0L;
        long free = 0;
        Set<MTEHatch> seen = Collections.newSetFromMap(new IdentityHashMap<MTEHatch, Boolean>());
        for (MTEHatch hatch : centralDynamoHatches) {
            if (hatch == null || hatch.getBaseMetaTileEntity() == null || !seen.add(hatch)) continue;
            long storage = Math.max(0L, hatch.maxEUStore() - hatch.getEUVar());
            long throughput = saturatingMultiply(hatch.maxEUOutput(), hatch.maxAmperesOut());
            long used = centralDynamoUsedThisTick.getOrDefault(hatch, 0L);
            free = saturatingAdd(free, Math.min(storage, Math.max(0L, throughput - used)));
        }
        return free;
    }

    private boolean drainFusionStartupEnergy(long amount) {
        if (amount <= 0) return true;
        // Ignition is an instantaneous withdrawal from stored EU, not a recurring EU/t load.
        if (owner.isWirelessMode()) {
            if (!owner.consumeWirelessEnergy(amount, 1)) return false;
            progression.recordStartupEnergy(amount);
            return true;
        }
        long stored = 0;
        for (MTEHatch hatch : centralEnergyHatches) {
            if (hatch == null || hatch.getBaseMetaTileEntity() == null) continue;
            stored = saturatingAdd(stored, hatch.getBaseMetaTileEntity().getStoredEU());
        }
        if (stored < amount || !ExoticEnergyInputHelper.drainEnergy(amount, centralEnergyHatches)) return false;
        progression.recordStartupEnergy(amount);
        return true;
    }

    private boolean drainCentralEnergy(long amount) {
        if (amount <= 0) return true;
        if (owner.isWirelessMode()) {
            if (!owner.consumeWirelessEnergy(amount, Math.max(1, getActiveMaxProgress() - getActiveProgress()))) return false;
            energySpentThisTick = saturatingAdd(energySpentThisTick, amount);
            return true;
        }
        long projectedUsage = saturatingAdd(energySpentThisTick, amount);
        if (projectedUsage > centralPowerCapacity) return false;
        long stored = 0;
        for (MTEHatch hatch : centralEnergyHatches) {
            if (hatch == null || hatch.getBaseMetaTileEntity() == null) continue;
            stored = saturatingAdd(stored, hatch.getBaseMetaTileEntity().getStoredEU());
        }
        if (stored < amount || !ExoticEnergyInputHelper.drainEnergy(amount, centralEnergyHatches)) return false;
        energySpentThisTick = projectedUsage;
        return true;
    }

    private long getCentralEnergyAvailableThisTick() {
        if (owner.isWirelessMode()) return Long.MAX_VALUE;
        long throughput = Math.max(0L, centralPowerCapacity - energySpentThisTick);
        if (throughput <= 0L) return 0L;
        long stored = 0L;
        Set<MTEHatch> seen = Collections.newSetFromMap(new IdentityHashMap<MTEHatch, Boolean>());
        for (MTEHatch hatch : centralEnergyHatches) {
            if (hatch == null || hatch.getBaseMetaTileEntity() == null || !seen.add(hatch)) continue;
            stored = saturatingAdd(stored, hatch.getBaseMetaTileEntity().getStoredEU());
        }
        return Math.min(throughput, stored);
    }

    private long drainCentralEnergyUpTo(long requested) {
        long amount = Math.min(Math.max(0L, requested), getCentralEnergyAvailableThisTick());
        if (amount <= 0L || !ExoticEnergyInputHelper.drainEnergy(amount, centralEnergyHatches)) return 0L;
        energySpentThisTick = saturatingAdd(energySpentThisTick, amount);
        return amount;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0 && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static int saturatingParallelAdd(int left, int right) {
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }

    static long getDynamoCapacity(MTEMultiBlockBase machine) {
        List<MTEHatch> hatches = new ArrayList<MTEHatch>(machine.mDynamoHatches.size() + 1);
        hatches.addAll(machine.mDynamoHatches);
        hatches.addAll(machine.getExoticDynamoHatches());
        return getDynamoCapacity(hatches);
    }

    private static long getDynamoCapacity(List<? extends MTEHatch> hatches) {
        Set<MTEHatch> seen = Collections.newSetFromMap(new IdentityHashMap<MTEHatch, Boolean>());
        long capacity = 0;
        for (MTEHatch hatch : hatches) {
            if (hatch == null || hatch.getBaseMetaTileEntity() == null || !seen.add(hatch)) continue;
            capacity = saturatingAdd(capacity, saturatingMultiply(hatch.maxEUOutput(), hatch.maxAmperesOut()));
        }
        return capacity;
    }

    private static boolean isSupportedGenerator(MTEMultiBlockBase remote) {
        return remote != null && HostedGeneratorSupport.isSupportedClassName(remote.getClass().getName())
            && getDynamoCapacity(remote) > 0;
    }

    private long getCompletedRecipeCount(MTEMultiBlockBase remote) {
        try {
            Field field = MTEMultiBlockBase.class.getDeclaredField("processingLogic");
            field.setAccessible(true);
            Object logic = field.get(remote);
            if (logic == null) return 1;
            Method getter = logic.getClass().getMethod("getCurrentParallels");
            int currentParallels = ((Number) getter.invoke(logic)).intValue();
            return Math.max(currentParallels, remote.lastParallel);
        } catch (ReflectiveOperationException error) {
            MatterBlueprints.LOG.warn("Could not read parallel count for {}; using one completed recipe", remote.mName, error);
            return 1;
        }
    }

    /** Resolves version- and machine-specific lifecycle hooks once per remote controller class. */
    private static final class RemoteHooks {

        private final Method actualEnergyUsage;
        private final Method incrementProgressTime;
        private final Method outputAfterRecipe;
        private final Method powerFlow;
        private final Field longEnergyOutput;
        private final Field trueOutput;
        private final Field chemicalEfficiency;
        private final Method turbineRefill;
        private final boolean hasCustomRunningTick;

        RemoteHooks(Class<?> type) {
            actualEnergyUsage = findLifecycleMethod(type, "getActualEnergyUsage");
            incrementProgressTime = findLifecycleMethod(type, "incrementProgressTime");
            outputAfterRecipe = findLifecycleMethod(type, "outputAfterRecipe");
            powerFlow = findLifecycleMethod(type, "getPowerFlow");
            longEnergyOutput = findOptionalField(type, "lEUt");
            trueOutput = findOptionalField(type, "trueOutput");
            chemicalEfficiency = findOptionalField(type, "tEff");
            turbineRefill = findLifecycleMethod(type, "tryRefillTurbineHolder");
            hasCustomRunningTick = declaresBeforeBase(type, "onRunningTick", ItemStack.class);
        }

        boolean invokeTurbineRefill(MTEMultiBlockBase remote) {
            if (turbineRefill == null) return false;
            try {
                turbineRefill.invoke(remote);
                return true;
            } catch (ReflectiveOperationException error) {
                MatterBlueprints.LOG.warn("Could not refill turbines for {}", remote.mName, error);
                return false;
            }
        }

        long getActualEnergyUsage(MTEMultiBlockBase remote) {
            if (actualEnergyUsage == null) return fallbackEnergyUsage(remote);
            if (actualEnergyUsage.getDeclaringClass() == MTEMultiBlockBase.class) {
                return Math.max(0L, -(long) remote.mEUt * 10000L / Math.max(1000, remote.mEfficiency));
            }
            try {
                return Math.max(0L, ((Number) actualEnergyUsage.invoke(remote)).longValue());
            } catch (ReflectiveOperationException error) {
                MatterBlueprints.LOG.warn("Could not read actual energy usage for {}", remote.mName, error);
                return fallbackEnergyUsage(remote);
            }
        }

        long getLongEUt(MTEMultiBlockBase remote) {
            if (longEnergyOutput == null) return 0L;
            try {
                return longEnergyOutput.getLong(remote);
            } catch (IllegalAccessException error) {
                MatterBlueprints.LOG.warn("Could not read extended hosted EU/t for {}", remote.mName, error);
                return 0L;
            }
        }

        void setLongEUt(MTEMultiBlockBase remote, long value) {
            if (longEnergyOutput == null) return;
            try {
                longEnergyOutput.setLong(remote, value);
            } catch (IllegalAccessException error) {
                MatterBlueprints.LOG.warn("Could not stage extended hosted EU/t for {}", remote.mName, error);
            }
        }

        void incrementProgress(MTEMultiBlockBase remote) {
            if (incrementProgressTime == null
                || incrementProgressTime.getDeclaringClass() == MTEMultiBlockBase.class) {
                remote.mProgresstime++;
                return;
            }
            try {
                incrementProgressTime.invoke(remote);
            } catch (ReflectiveOperationException error) {
                MatterBlueprints.LOG.warn("Could not increment progress for {}", remote.mName, error);
                remote.mProgresstime++;
            }
        }

        void invokeOutputAfterRecipe(MTEMultiBlockBase remote) {
            if (outputAfterRecipe == null) return;
            try {
                outputAfterRecipe.invoke(remote);
            } catch (ReflectiveOperationException error) {
                MatterBlueprints.LOG.warn("Could not run outputAfterRecipe for {}", remote.mName, error);
            }
        }

        long getGeneratedPower(MTEMultiBlockBase remote) {
            try {
                if (trueOutput != null) return Math.max(0L, trueOutput.getLong(remote));
                if (powerFlow != null && chemicalEfficiency != null) {
                    long basePower = ((Number) powerFlow.invoke(remote)).longValue();
                    long efficiency = chemicalEfficiency.getLong(remote);
                    return Math.max(0L, saturatingMultiply(basePower, efficiency) / 10000L);
                }
                if (longEnergyOutput != null) {
                    long basePower = longEnergyOutput.getLong(remote);
                    return Math.max(0L, saturatingMultiply(basePower, Math.max(0, remote.mEfficiency)) / 10000L);
                }
            } catch (ReflectiveOperationException error) {
                MatterBlueprints.LOG.warn("Could not read generated power for {}", remote.mName, error);
            }
            return remote.mEUt > 0
                ? Math.max(0L, saturatingMultiply(remote.mEUt, Math.max(0, remote.mEfficiency)) / 10000L)
                : 0L;
        }

        private static long fallbackEnergyUsage(MTEMultiBlockBase remote) {
            return remote.mEUt < 0 ? -(long) remote.mEUt : 0L;
        }

        private static Method findLifecycleMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            Class<?> current = type;
            while (current != null && MTEMultiBlockBase.class.isAssignableFrom(current)) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                } catch (LinkageError | SecurityException unavailableOnThisSide) {
                    // Dedicated servers do not contain net.minecraft.client classes. Some GT machine classes declare
                    // unrelated client-only methods, and HotSpot resolves every declared signature while answering
                    // getDeclaredMethod(). Skip that class and retain the safe base implementation instead of taking
                    // down the server tick with NoClassDefFoundError.
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private static Field findOptionalField(Class<?> type, String name) {
            Class<?> current = type;
            while (current != null && MTEMultiBlockBase.class.isAssignableFrom(current)) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                } catch (LinkageError | SecurityException unavailableOnThisSide) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private static boolean declaresBeforeBase(Class<?> type, String name, Class<?>... parameterTypes) {
            Class<?> current = type;
            while (current != null && current != MTEMultiBlockBase.class
                && MTEMultiBlockBase.class.isAssignableFrom(current)) {
                try {
                    current.getDeclaredMethod(name, parameterTypes);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                } catch (LinkageError | SecurityException unavailableOnThisSide) {
                    current = current.getSuperclass();
                }
            }
            return false;
        }
    }

    private static final class HatchSnapshot {

        private final ArrayList<MTEHatchInput> inputHatches;
        private final ArrayList<MTEHatchOutput> outputHatches;
        private final ArrayList<MTEHatchInputBus> inputBusses;
        private final ArrayList<MTEHatchOutputBus> outputBusses;
        private final ArrayList<IDualInputHatch> dualInputHatches;

        HatchSnapshot(MTEMultiBlockBase remote) {
            inputHatches = remote.mInputHatches;
            outputHatches = remote.mOutputHatches;
            inputBusses = remote.mInputBusses;
            outputBusses = remote.mOutputBusses;
            dualInputHatches = remote.mDualInputHatches;
        }

        void useOwnerIO(MTEHostedMachineController owner, MTEMultiBlockBase remote) {
            remote.mInputHatches = owner.mInputHatches;
            remote.mOutputHatches = owner.mOutputHatches;
            remote.mInputBusses = owner.mInputBusses;
            remote.mOutputBusses = owner.mOutputBusses;
            remote.mDualInputHatches = owner.mDualInputHatches;
        }

        void restore(MTEMultiBlockBase remote) {
            remote.mInputHatches = inputHatches;
            remote.mOutputHatches = outputHatches;
            remote.mInputBusses = inputBusses;
            remote.mOutputBusses = outputBusses;
            remote.mDualInputHatches = dualInputHatches;
        }
    }

    /** Expands a representative machine's read-only voltage model to the hosted machine bank. */
    private static final class AggregateCapacitySnapshot {

        private final ArrayList<MTEHatchEnergy> energyHatches;
        private final List<MTEHatch> exoticEnergyHatches;
        private final Object processingLogic;
        private final Supplier<Integer> maxParallelSupplier;
        private final int maxParallel;
        private Runnable restorePower;
        private HostedProcessingSettings processingSettings;

        @SuppressWarnings("unchecked")
        AggregateCapacitySnapshot(MTEMultiBlockBase representative, Set<MTEMultiBlockBase> machines,
            int parallelCapacity, long nativePowerBudget, boolean perfectOverclock,
            double durationMultiplier, boolean batch) {
            energyHatches = representative.mEnergyHatches;
            exoticEnergyHatches = representative.getExoticEnergyHatches();

            final int aggregateParallel = Math.max(1, parallelCapacity);

            ArrayList<MTEHatchEnergy> aggregateNormal = new ArrayList<MTEHatchEnergy>();
            List<MTEHatch> aggregateExotic = new ArrayList<MTEHatch>();
            for (int i = 0; i < machines.size(); i++) {
                aggregateNormal.addAll(energyHatches);
                aggregateExotic.addAll(exoticEnergyHatches);
            }
            representative.mEnergyHatches = aggregateNormal;
            setExoticEnergyHatches(representative, aggregateExotic);

            Object logic = null;
            Supplier<Integer> previousSupplier = null;
            int previousMaxParallel = 1;
            try {
                Field processingField = MTEMultiBlockBase.class.getDeclaredField("processingLogic");
                processingField.setAccessible(true);
                logic = processingField.get(representative);
                if (logic != null) {
                    Field supplierField = findField(logic.getClass(), "maxParallelSupplier");
                    supplierField.setAccessible(true);
                    previousSupplier = (Supplier<Integer>) supplierField.get(logic);
                    Field maxField = findField(logic.getClass(), "maxParallel");
                    maxField.setAccessible(true);
                    previousMaxParallel = maxField.getInt(logic);
                    Method setSupplier = logic.getClass()
                        .getMethod("setMaxParallelSupplier", Supplier.class);
                    ProcessingLogic nativeLogic = (ProcessingLogic) logic;
                    Field voltageField = findField(logic.getClass(), "availableVoltage");
                    Field amperageField = findField(logic.getClass(), "availableAmperage");
                    voltageField.setAccessible(true);
                    amperageField.setAccessible(true);
                    Field timeReduction = findField(logic.getClass(), "overClockTimeReduction");
                    Field powerIncrease = findField(logic.getClass(), "overClockPowerIncrease");
                    timeReduction.setAccessible(true);
                    powerIncrease.setAccessible(true);
                    double[] originalOverclock = new double[2];
                    long[] originalPower = new long[2];
                    boolean[] powerCaptured = new boolean[1];
                    restorePower = () -> {
                        if (!powerCaptured[0]) return;
                        nativeLogic.setAvailableVoltage(originalPower[0]);
                        nativeLogic.setAvailableAmperage(originalPower[1]);
                        if (perfectOverclock) nativeLogic.setOverclock(originalOverclock[0], originalOverclock[1]);
                    };
                    // process() resolves this supplier after setupProcessingLogic()/setProcessingLogicPower().
                    // Capping earlier would be overwritten by the remote's native machine setup.
                    setSupplier.invoke(logic, (Supplier<Integer>) () -> {
                        try {
                            if (processingSettings == null) {
                                processingSettings = new HostedProcessingSettings(nativeLogic, durationMultiplier, batch);
                            }
                            if (!powerCaptured[0]) {
                                originalPower[0] = voltageField.getLong(nativeLogic);
                                originalPower[1] = amperageField.getLong(nativeLogic);
                                originalOverclock[0] = timeReduction.getDouble(nativeLogic);
                                originalOverclock[1] = powerIncrease.getDouble(nativeLogic);
                                powerCaptured[0] = true;
                            }
                            if (perfectOverclock) nativeLogic.enablePerfectOverclock();
                            long voltage = Math.max(1L, voltageField.getLong(nativeLogic));
                            long amps = Math.max(1L, amperageField.getLong(nativeLogic));
                            if (HostedFusionSupport.isFusion(representative)) {
                                amps = HostedFusionSupport.aggregateAmperage(originalPower[1], machines.size());
                            }
                            long cappedVoltage = Math.min(voltage, nativePowerBudget);
                            nativeLogic.setAvailableVoltage(cappedVoltage);
                            nativeLogic.setAvailableAmperage(cappedVoltage <= 0 ? 0L
                                : Math.min(amps, nativePowerBudget / cappedVoltage));
                            return aggregateParallel;
                        } catch (IllegalAccessException error) {
                            throw new IllegalStateException("Cannot apply hosted power budget", error);
                        }
                    });
                    Method setMax = logic.getClass().getMethod("setMaxParallel", int.class);
                    setMax.invoke(logic, aggregateParallel);
                }
            } catch (ReflectiveOperationException error) {
                MatterBlueprints.LOG.warn("Could not aggregate ProcessingLogic parallel capacity", error);
            }
            processingLogic = logic;
            maxParallelSupplier = previousSupplier;
            maxParallel = previousMaxParallel;
        }

        void restore(MTEMultiBlockBase representative) {
            if (processingSettings != null) processingSettings.close();
            representative.mEnergyHatches = energyHatches;
            setExoticEnergyHatches(representative, exoticEnergyHatches);
            if (restorePower != null) restorePower.run();
            if (processingLogic == null) return;
            try {
                Method setSupplier = processingLogic.getClass()
                    .getMethod("setMaxParallelSupplier", Supplier.class);
                setSupplier.invoke(processingLogic, maxParallelSupplier);
                Method setMax = processingLogic.getClass().getMethod("setMaxParallel", int.class);
                setMax.invoke(processingLogic, maxParallel);
            } catch (ReflectiveOperationException error) {
                MatterBlueprints.LOG.warn("Could not restore ProcessingLogic parallel capacity", error);
            }
        }

        private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
            Class<?> current = type;
            while (current != null) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }
    }

    /** Gives the native ProcessingLogic cache a preplanned recipe and finds distinct low-input tasks efficiently. */
    private static final class RecipePrioritySnapshot {

        private final GTRecipe preferred;

        RecipePrioritySnapshot(MTEMultiBlockBase remote, GTRecipe preferred) {
            this.preferred = preferred;
            if (preferred == null) return;
            try {
                Field processingField = findField(MTEMultiBlockBase.class, "processingLogic");
                processingField.setAccessible(true);
                Object logic = processingField.get(remote);
                if (logic != null) {
                    Field last = findField(logic.getClass(), "lastRecipe");
                    last.setAccessible(true);
                    last.set(logic, preferred);
                    Field lastMap = findField(logic.getClass(), "lastRecipeMap");
                    lastMap.setAccessible(true);
                    lastMap.set(logic, remote.getRecipeMap());
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                MatterBlueprints.LOG.debug("Could not prioritize hosted recipes for {}", remote.mName, error);
            }
        }

        static List<GTRecipe> findPreferredRecipes(MTEMultiBlockBase remote, ItemStack[] items, FluidStack[] fluids,
            Set<String> excluded, int limit) {
            if (limit <= 0) return Collections.emptyList();
            RecipeMap<?> map = remote.getRecipeMap();
            if (map == null) return Collections.emptyList();
            List<GTRecipe> matches = new ArrayList<GTRecipe>();
            map.findRecipeQuery().items(items).fluids(fluids).findAll().forEach(recipe -> {
                if (recipe != null && !excluded.contains(recipeKey(recipe))) matches.add(recipe);
            });
            matches.sort(new Comparator<GTRecipe>() {

                @Override
                public int compare(GTRecipe left, GTRecipe right) {
                    int amount = Long.compare(inputAmount(left), inputAmount(right));
                    if (amount != 0) return amount;
                    int kinds = Integer.compare(inputKinds(left), inputKinds(right));
                    if (kinds != 0) return kinds;
                    return Integer.compare(left.mEUt, right.mEUt);
                }
            });
            List<GTRecipe> distinct = new ArrayList<GTRecipe>(Math.min(limit, matches.size()));
            Set<String> keys = new HashSet<String>(excluded);
            for (GTRecipe recipe : matches) {
                String key = recipeKey(recipe);
                if (!keys.add(key)) continue;
                distinct.add(recipe);
                if (distinct.size() >= limit) break;
            }
            return distinct;
        }

        String getSelectedRecipeKey(MTEMultiBlockBase remote) {
            try {
                Field processingField = findField(MTEMultiBlockBase.class, "processingLogic");
                processingField.setAccessible(true);
                Object logic = processingField.get(remote);
                if (logic != null) {
                    Field last = findField(logic.getClass(), "lastRecipe");
                    last.setAccessible(true);
                    Object actual = last.get(logic);
                    if (actual instanceof GTRecipe) return recipeKey((GTRecipe) actual);
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                MatterBlueprints.LOG.debug("Could not identify hosted recipe for {}", remote.mName, error);
            }
            return preferred == null ? "" : recipeKey(preferred);
        }

        private static long inputAmount(GTRecipe recipe) {
            long amount = 0;
            if (recipe.mInputs != null) {
                for (ItemStack stack : recipe.mInputs) if (stack != null) amount = saturatingAdd(amount, stack.stackSize);
            }
            if (recipe.mFluidInputs != null) {
                for (FluidStack stack : recipe.mFluidInputs) {
                    if (stack != null) amount = saturatingAdd(amount, (stack.amount + 999L) / 1000L);
                }
            }
            return amount;
        }

        private static int inputKinds(GTRecipe recipe) {
            int kinds = 0;
            if (recipe.mInputs != null) for (ItemStack stack : recipe.mInputs) if (stack != null) kinds++;
            if (recipe.mFluidInputs != null) for (FluidStack stack : recipe.mFluidInputs) if (stack != null) kinds++;
            return kinds;
        }

        private static String recipeKey(GTRecipe recipe) {
            StringBuilder key = new StringBuilder(128).append(recipe.mEUt).append('/').append(recipe.mDuration);
            appendItems(key, recipe.mInputs);
            appendFluids(key, recipe.mFluidInputs);
            appendItems(key, recipe.mOutputs);
            appendFluids(key, recipe.mFluidOutputs);
            return key.toString();
        }

        private static void appendItems(StringBuilder key, ItemStack[] stacks) {
            key.append('|');
            if (stacks == null) return;
            for (ItemStack stack : stacks) {
                if (stack == null) continue;
                key.append(Item.itemRegistry.getNameForObject(stack.getItem())).append('@')
                    .append(stack.getItemDamage()).append('x').append(stack.stackSize).append(';');
            }
        }

        private static void appendFluids(StringBuilder key, FluidStack[] stacks) {
            key.append('|');
            if (stacks == null) return;
            for (FluidStack stack : stacks) {
                if (stack == null || stack.getFluid() == null) continue;
                key.append(stack.getFluid().getName()).append('x').append(stack.amount).append(';');
            }
        }

        private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
            Class<?> current = type;
            while (current != null) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }
    }

    /** Temporarily exposes one additional hatch tier while the native machine validates and overclocks its recipe. */
    private static final class VoltageTierSnapshot {

        private static final Field TIER_FIELD = findTierField();
        private final List<MTETieredMachineBlock> hatches = new ArrayList<MTETieredMachineBlock>();
        private final List<Byte> tiers = new ArrayList<Byte>();

        VoltageTierSnapshot(MTEMultiBlockBase remote, boolean enabled) {
            if (!enabled || TIER_FIELD == null || HostedSpaceAssemblerSupport.isSpaceAssembler(remote)) return;
            Set<MTEHatch> unique = Collections.newSetFromMap(new IdentityHashMap<MTEHatch, Boolean>());
            unique.addAll(remote.mEnergyHatches);
            unique.addAll(remote.getExoticEnergyHatches());
            try {
                for (MTEHatch hatch : unique) {
                    if (!(hatch instanceof MTETieredMachineBlock)) continue;
                    MTETieredMachineBlock tiered = (MTETieredMachineBlock) hatch;
                    byte tier = TIER_FIELD.getByte(tiered);
                    if (tier >= Byte.MAX_VALUE) continue;
                    hatches.add(tiered);
                    tiers.add(tier);
                    TIER_FIELD.setByte(tiered, (byte) (tier + 1));
                }
            } catch (IllegalAccessException error) {
                MatterBlueprints.LOG.warn("Could not apply hosted voltage extension", error);
                restore();
            }
        }

        VoltageTierSnapshot(List<? extends MTEHatch> source, byte forcedTier) {
            if (TIER_FIELD == null) return;
            Set<MTEHatch> unique = Collections.newSetFromMap(new IdentityHashMap<MTEHatch, Boolean>());
            unique.addAll(source);
            try {
                for (MTEHatch hatch : unique) {
                    if (!(hatch instanceof MTETieredMachineBlock)) continue;
                    MTETieredMachineBlock tiered = (MTETieredMachineBlock) hatch;
                    byte tier = TIER_FIELD.getByte(tiered);
                    hatches.add(tiered);
                    tiers.add(tier);
                    TIER_FIELD.setByte(tiered, forcedTier);
                }
            } catch (IllegalAccessException error) {
                MatterBlueprints.LOG.warn("Could not stage central energy hatch voltage for hosted processing", error);
                restore();
            }
        }

        void restore() {
            if (TIER_FIELD == null) return;
            for (int i = 0; i < hatches.size(); i++) {
                try {
                    TIER_FIELD.setByte(hatches.get(i), tiers.get(i));
                } catch (IllegalAccessException error) {
                    MatterBlueprints.LOG.error("Could not restore hosted energy hatch tier", error);
                }
            }
            hatches.clear();
            tiers.clear();
        }

        private static Field findTierField() {
            try {
                Field field = MTETieredMachineBlock.class.getDeclaredField("mTier");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException error) {
                MatterBlueprints.LOG.warn("Hosted voltage extension is unavailable", error);
                return null;
            }
        }
    }

    private static void setExoticEnergyHatches(MTEMultiBlockBase machine, List<MTEHatch> hatches) {
        if (EXOTIC_ENERGY_HATCHES_FIELD == null) return;
        try {
            EXOTIC_ENERGY_HATCHES_FIELD.set(machine, hatches);
        } catch (IllegalAccessException error) {
            MatterBlueprints.LOG.error("Could not swap exotic energy hatches for aggregation", error);
        }
    }

    private static Field resolveExoticEnergyHatchesField() {
        return resolveHatchField("mExoticEnergyHatches");
    }

    private static Field resolveHatchField(String name) {
        try {
            Field field = MTEMultiBlockBase.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            MatterBlueprints.LOG.error("Could not resolve multiblock hatch field {}", name, error);
            return null;
        }
    }

    /**
     * Compatibility scope for a remote's custom onRunningTick hook. The host already withdrew the recipe's EU before
     * entering this scope. Making the base GT energy check a no-op preserves custom running mechanics without emptying
     * and refilling real remote energy hatches every tick, which made their power state visibly oscillate.
     */
    private static final class RemoteRunningContext {

        private final MTEMultiBlockBase representative;
        private final RemoteHooks hooks;
        private int savedEUt;
        private long savedLongEUt;
        private boolean staged;

        RemoteRunningContext(MTEMultiBlockBase representative, Set<MTEMultiBlockBase> machines) {
            this.representative = representative;
            hooks = REMOTE_HOOKS.get(representative.getClass());
        }

        void stage(MTEMultiBlockBase remote, long amount) {
            savedEUt = remote.mEUt;
            savedLongEUt = hooks.getLongEUt(remote);
            remote.mEUt = 0;
            hooks.setLongEUt(remote, 0L);
            staged = true;
        }

        void restore(MTEMultiBlockBase remote) {
            if (!staged) return;
            remote.mEUt = savedEUt;
            hooks.setLongEUt(remote, savedLongEUt);
            staged = false;
        }
    }

    /** Lets the original running hook validate its own dynamos without leaving generated EU in the remote machine. */
    private static final class DynamoEnergySnapshot {

        private final MTEHatch[] dynamos;
        private final long[] storedEnergy;

        DynamoEnergySnapshot(MTEMultiBlockBase remote) {
            Set<MTEHatch> unique = Collections.newSetFromMap(new IdentityHashMap<MTEHatch, Boolean>());
            for (MTEHatch hatch : remote.mDynamoHatches) if (hatch != null) unique.add(hatch);
            for (MTEHatch hatch : remote.getExoticDynamoHatches()) if (hatch != null) unique.add(hatch);
            dynamos = unique.toArray(new MTEHatch[unique.size()]);
            storedEnergy = new long[dynamos.length];
            for (int i = 0; i < dynamos.length; i++) storedEnergy[i] = dynamos[i].getEUVar();
        }

        void restore() {
            for (int i = 0; i < dynamos.length; i++) dynamos[i].setEUVar(storedEnergy[i]);
        }
    }

    static final class HostedJob {
        long plasmaRuntime = -1L;
        float thermalGrowth = -1F;
        int thermalTicks;
        int thermalBooster = -2;
        private boolean equivalentAssembly;
        private int assemblyUnits;

        private int progress;
        private int maxProgress;
        private ItemStack[] outputItems;
        private FluidStack[] outputFluids;
        private int machineCount;
        private int parallels;
        private int reservedParallel;
        private String recipeKey = "";
        private long energyUsage;
        private long energyCredit;
        private int recipeEUt;
        private long recipeLongEUt;
        private long nextRecipeCheckTick;
        private int idleRecipeCheckDelay;

        boolean isActive() {
            return maxProgress > 0;
        }

        void captureFromRemote(MTEMultiBlockBase remote, int machineCount, long parallels, long energyUsage) {
            progress = remote.mProgresstime;
            maxProgress = remote.mMaxProgresstime;
            outputItems = remote.mOutputItems;
            outputFluids = remote.mOutputFluids;
            this.machineCount = Math.max(1, machineCount);
            this.parallels = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, parallels));
            this.reservedParallel = this.parallels;
            this.energyUsage = Math.max(0L, energyUsage);
            recipeEUt = remote.mEUt;
            recipeLongEUt = REMOTE_HOOKS.get(remote.getClass()).getLongEUt(remote);
            hideProgressFromRemote(remote);
        }

        void exposeProgressToRemote(MTEMultiBlockBase remote) {
            remote.mProgresstime = progress;
            remote.mMaxProgresstime = maxProgress;
            remote.mEUt = recipeEUt;
            REMOTE_HOOKS.get(remote.getClass()).setLongEUt(remote, recipeLongEUt);
        }

        void hideProgressFromRemote(MTEMultiBlockBase remote) {
            remote.mProgresstime = 0;
            remote.mMaxProgresstime = 0;
            // A disabled GT controller can still pass through base ticking paths in some forks. Never leave the
            // hosted recipe's negative EU/t on the physical controller between coordinator ticks.
            remote.mEUt = 0;
            REMOTE_HOOKS.get(remote.getClass()).setLongEUt(remote, 0L);
            remote.mOutputItems = null;
            remote.mOutputFluids = null;
        }

        void restoreToRemote(MTEMultiBlockBase remote) {
            if (equivalentAssembly) throw new IllegalStateException("Equivalent assembly jobs must remain in host storage");
            HostedThermalMachineSupport.release(remote, this);
            if (!isActive()) return;
            remote.mProgresstime = progress;
            remote.mMaxProgresstime = maxProgress;
            remote.mEUt = recipeEUt;
            REMOTE_HOOKS.get(remote.getClass()).setLongEUt(remote, recipeLongEUt);
            remote.mOutputItems = outputItems;
            remote.mOutputFluids = outputFluids;
            clear();
        }

        void clear() {
            equivalentAssembly = false;
            assemblyUnits = 0;
            progress = 0;
            maxProgress = 0;
            outputItems = null;
            outputFluids = null;
            machineCount = 0;
            parallels = 0;
            reservedParallel = 0;
            recipeKey = "";
            energyUsage = 0;
            energyCredit = 0;
            recipeEUt = 0;
            recipeLongEUt = 0L;
        }

        void scheduleNextRecipeCheck(long worldTick, boolean started) {
            int baseDelay = Math.max(1, BlueprintConfig.hostedMachineRecipeCheckIntervalTicks);
            if (started) {
                idleRecipeCheckDelay = baseDelay;
            } else if (idleRecipeCheckDelay <= 0) {
                idleRecipeCheckDelay = Math.min(MAX_IDLE_RECIPE_CHECK_TICKS, baseDelay * 2);
            } else {
                idleRecipeCheckDelay = Math.min(MAX_IDLE_RECIPE_CHECK_TICKS, idleRecipeCheckDelay * 2);
            }
            nextRecipeCheckTick = worldTick + Math.max(baseDelay, idleRecipeCheckDelay);
        }

        void writeToNBT(NBTTagCompound tag) {
            tag.setInteger("progress", progress);
            tag.setInteger("maxProgress", maxProgress);
            tag.setInteger("machineCount", machineCount);
            tag.setInteger("parallels", parallels);
            tag.setInteger("reservedParallel", reservedParallel);
            tag.setString("recipeKey", recipeKey == null ? "" : recipeKey);
            tag.setLong("energyUsage", energyUsage);
            tag.setLong("energyCredit", energyCredit);
            tag.setLong("plasmaRuntime", plasmaRuntime);
            tag.setFloat("thermalGrowth", thermalGrowth);
            tag.setInteger("thermalTicks", thermalTicks);
            tag.setInteger("thermalBooster", thermalBooster);
            tag.setBoolean("equivalentAssembly", equivalentAssembly);
            tag.setInteger("assemblyUnits", assemblyUnits);
            tag.setInteger("recipeEUt", recipeEUt);
            tag.setLong("recipeLongEUt", recipeLongEUt);
            tag.setLong("nextRecipeCheckTick", nextRecipeCheckTick);
            tag.setInteger("idleRecipeCheckDelay", idleRecipeCheckDelay);
            writeItems(tag, outputItems);
            writeFluids(tag, outputFluids);
        }

        static HostedJob readFromNBT(NBTTagCompound tag) {
            HostedJob job = new HostedJob();
            job.progress = Math.max(0, tag.getInteger("progress"));
            job.maxProgress = Math.max(0, tag.getInteger("maxProgress"));
            job.machineCount = Math.max(0, tag.getInteger("machineCount"));
            job.parallels = Math.max(0, tag.getInteger("parallels"));
            job.reservedParallel = tag.hasKey("reservedParallel")
                ? Math.max(0, tag.getInteger("reservedParallel")) : job.parallels;
            job.recipeKey = tag.getString("recipeKey");
            job.energyUsage = Math.max(0L, tag.getLong("energyUsage"));
            job.energyCredit = Math.max(0L, Math.min(job.energyUsage, tag.getLong("energyCredit")));
            job.plasmaRuntime = tag.hasKey("plasmaRuntime") ? Math.max(-1L, tag.getLong("plasmaRuntime")) : -1L;
            job.thermalGrowth = tag.hasKey("thermalGrowth") ? tag.getFloat("thermalGrowth") : -1F;
            if (Float.isNaN(job.thermalGrowth) || Float.isInfinite(job.thermalGrowth)) job.thermalGrowth = -1F;
            job.thermalTicks = tag.getInteger("thermalTicks");
            job.thermalBooster = tag.hasKey("thermalBooster") ? Math.max(-2, tag.getInteger("thermalBooster")) : -2;
            job.equivalentAssembly = tag.getBoolean("equivalentAssembly");
            job.assemblyUnits = job.equivalentAssembly ? Math.max(1, tag.getInteger("assemblyUnits")) : 0;
            job.recipeEUt = tag.hasKey("recipeEUt") ? tag.getInteger("recipeEUt")
                : (int) -Math.min(Integer.MAX_VALUE, job.energyUsage);
            job.recipeLongEUt = tag.hasKey("recipeLongEUt") ? tag.getLong("recipeLongEUt") : -job.energyUsage;
            job.nextRecipeCheckTick = Math.max(0L, tag.getLong("nextRecipeCheckTick"));
            job.idleRecipeCheckDelay = Math.max(0, tag.getInteger("idleRecipeCheckDelay"));
            job.outputItems = readItems(tag);
            job.outputFluids = readFluids(tag);
            return job;
        }

        private static void writeItems(NBTTagCompound tag, ItemStack[] stacks) {
            if (stacks == null) return;
            tag.setInteger("itemOutputCount", stacks.length);
            NBTTagList list = new NBTTagList();
            for (int i = 0; i < stacks.length; i++) {
                if (stacks[i] == null) continue;
                NBTTagCompound stackTag = new NBTTagCompound();
                stackTag.setInteger("slot", i);
                stacks[i].writeToNBT(stackTag);
                list.appendTag(stackTag);
            }
            tag.setTag("itemOutputs", list);
        }

        private static ItemStack[] readItems(NBTTagCompound tag) {
            int count = tag.getInteger("itemOutputCount");
            if (count <= 0) return null;
            ItemStack[] result = new ItemStack[count];
            NBTTagList list = tag.getTagList("itemOutputs", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound stackTag = list.getCompoundTagAt(i);
                int slot = stackTag.getInteger("slot");
                if (slot >= 0 && slot < result.length) result[slot] = ItemStack.loadItemStackFromNBT(stackTag);
            }
            return result;
        }

        private static void writeFluids(NBTTagCompound tag, FluidStack[] stacks) {
            if (stacks == null) return;
            tag.setInteger("fluidOutputCount", stacks.length);
            NBTTagList list = new NBTTagList();
            for (int i = 0; i < stacks.length; i++) {
                if (stacks[i] == null) continue;
                NBTTagCompound stackTag = new NBTTagCompound();
                stackTag.setInteger("slot", i);
                stacks[i].writeToNBT(stackTag);
                list.appendTag(stackTag);
            }
            tag.setTag("fluidOutputs", list);
        }

        private static FluidStack[] readFluids(NBTTagCompound tag) {
            int count = tag.getInteger("fluidOutputCount");
            if (count <= 0) return null;
            FluidStack[] result = new FluidStack[count];
            NBTTagList list = tag.getTagList("fluidOutputs", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound stackTag = list.getCompoundTagAt(i);
                int slot = stackTag.getInteger("slot");
                if (slot >= 0 && slot < result.length) result[slot] = FluidStack.loadFluidStackFromNBT(stackTag);
            }
            return result;
        }
    }
}

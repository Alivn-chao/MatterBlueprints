package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.MatterBlueprints;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.ExoticEnergyInputHelper;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.multi.drone.DroneConnection;
import gregtech.common.tileentities.machines.multi.drone.MTEHatchDroneDownLink;

final class HostedMachineCoordinator {

    private static final int CENTRAL_POWER_CACHE_TICKS = 20;
    private static final int MAINTENANCE_REFRESH_TICKS = 20;
    private static final ClassValue<RemoteHooks> REMOTE_HOOKS = new ClassValue<RemoteHooks>() {

        @Override
        protected RemoteHooks computeValue(Class<?> type) {
            return new RemoteHooks(type);
        }
    };

    private final MTEHostedMachineController owner;
    private final Map<MTEMultiBlockBase, HostedJob> hosted = new IdentityHashMap<MTEMultiBlockBase, HostedJob>();
    private final Set<String> persistedClaims = new HashSet<String>();
    private final Map<String, HostedJob> pendingJobs = new HashMap<String, HostedJob>();
    private final List<MTEHatch> centralEnergyHatches = new ArrayList<MTEHatch>();
    private MTEMultiBlockBase activeRemote;
    private String statusKey = "matterblueprints.host.status.no_match";
    private long energySpentThisTick;
    private long centralPowerCapacity;

    HostedMachineCoordinator(MTEHostedMachineController owner) {
        this.owner = owner;
    }

    void refresh() {
        MTEHatchDroneDownLink downLink = owner.getDroneDownLink();
        ItemStack selector = owner.getControllerSlot();
        if (downLink == null || downLink.getCentre() == null) {
            releaseAll();
            statusKey = "matterblueprints.host.status.no_link";
            return;
        }
        if (selector == null) {
            releaseAll();
            statusKey = "matterblueprints.host.status.no_selector";
            return;
        }

        Set<MTEMultiBlockBase> discovered = Collections
            .newSetFromMap(new IdentityHashMap<MTEMultiBlockBase, Boolean>());
        for (DroneConnection connection : downLink.getCentre().getConnectionList()) {
            if (connection == null || !connection.isValid()) continue;
            MTEMultiBlockBase remote = connection.getLinkedMachine();
            if (!isEligible(remote, connection, selector)) continue;
            discovered.add(remote);
            acquire(remote);
        }

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
        if (centralEnergyHatches.isEmpty() || worldTick % CENTRAL_POWER_CACHE_TICKS == 0) refreshCentralPower();
        if (activeRemote != null) {
            HostedJob job = hosted.get(activeRemote);
            if (job == null || !job.isActive() || !isUsable(activeRemote)) {
                if (job != null) job.restoreToRemote(activeRemote);
                activeRemote = null;
                setRemoteActivity(false);
            } else {
                advanceRecipe(activeRemote, job, worldTick);
            }
        }
        if (allowNewRecipe && activeRemote == null && !hosted.isEmpty()
            && worldTick % BlueprintConfig.hostedMachineRecipeCheckIntervalTicks == 0) {
            MTEMultiBlockBase representative = chooseRepresentative();
            if (representative != null) startRecipe(representative);
        }
    }

    void releaseAll() {
        Iterator<MTEMultiBlockBase> iterator = hosted.keySet().iterator();
        while (iterator.hasNext()) release(iterator.next(), iterator);
        persistedClaims.clear();
        pendingJobs.clear();
        activeRemote = null;
        centralEnergyHatches.clear();
        centralPowerCapacity = 0;
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

    void writeToNBT(NBTTagCompound tag) {
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
    }

    void readFromNBT(NBTTagCompound tag) {
        persistedClaims.clear();
        pendingJobs.clear();
        NBTTagList list = tag.getTagList("mbHostClaims", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound claimTag = list.getCompoundTagAt(i);
            String key = claimTag.getString("machine");
            if (key.isEmpty()) continue;
            persistedClaims.add(key);
            HostedJob job = HostedJob.readFromNBT(claimTag);
            if (job.isActive()) pendingJobs.put(key, job);
        }
    }

    int getHostedCount() {
        return hosted.size();
    }

    int getRunningCount() {
        return activeRemote != null && hosted.containsKey(activeRemote) && hosted.get(activeRemote).isActive() ? 1 : 0;
    }

    int getAggregateProgressPercent() {
        HostedJob job = getActiveJob();
        if (job == null || job.maxProgress <= 0) return 0;
        return Math.min(100, job.progress * 100 / job.maxProgress);
    }

    long getEnergySpentThisTick() {
        return energySpentThisTick;
    }

    long getCentralPowerCapacity() {
        return centralPowerCapacity;
    }

    List<String> getMachineStatusLines(int maximumLines) {
        List<String> result = new ArrayList<String>();
        HostedJob job = getActiveJob();
        if (job != null) {
            int percent = job.maxProgress <= 0 ? 0 : Math.min(100, job.progress * 100 / job.maxProgress);
            result.add(
                net.minecraft.util.StatCollector.translateToLocalFormatted(
                    "matterblueprints.host.info.aggregate_running",
                    job.machineCount,
                    job.parallels,
                    formatSeconds(job.progress),
                    formatSeconds(job.maxProgress),
                    percent,
                    job.energyUsage));
        } else if (!hosted.isEmpty()) {
            result.add(
                net.minecraft.util.StatCollector.translateToLocalFormatted(
                    "matterblueprints.host.info.aggregate_idle",
                    hosted.size()));
        }
        return result;
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
        HostedJob job = getActiveJob();
        return job == null ? 0 : job.parallels;
    }

    long getActiveEnergyUsage() {
        HostedJob job = getActiveJob();
        return job == null ? 0 : job.energyUsage;
    }

    int getActiveEfficiency() {
        return activeRemote == null ? 0 : activeRemote.mEfficiency;
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

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0D);
    }

    private HostedJob getActiveJob() {
        if (activeRemote == null) return null;
        HostedJob job = hosted.get(activeRemote);
        return job != null && job.isActive() ? job : null;
    }

    private boolean isUsable(MTEMultiBlockBase remote) {
        return remote != null && remote.isValid() && remote.mMachine && remote.getBaseMetaTileEntity() != null;
    }

    private MTEMultiBlockBase chooseRepresentative() {
        MTEMultiBlockBase result = null;
        long lowestVoltage = Long.MAX_VALUE;
        for (MTEMultiBlockBase remote : hosted.keySet()) {
            if (!isUsable(remote)) continue;
            long voltage = remote.getMaxInputVoltage();
            if (result == null || voltage < lowestVoltage) {
                result = remote;
                lowestVoltage = voltage;
            }
        }
        return result;
    }

    private void setRemoteActivity(boolean active) {
        for (MTEMultiBlockBase remote : hosted.keySet()) {
            IGregTechTileEntity tile = remote.getBaseMetaTileEntity();
            if (tile != null && remote.isValid()) tile.setActive(active);
        }
    }

    private boolean isEligible(MTEMultiBlockBase remote, DroneConnection connection, ItemStack selector) {
        if (remote == null || remote == owner || !remote.isValid() || !remote.mMachine) return false;
        if (!selector.isItemEqual(connection.getMachineItem())) return false;
        List<MTEHatch> energyHatches = remote.getExoticAndNormalEnergyHatchList();
        return !energyHatches.isEmpty() && ExoticEnergyInputHelper.getTotalEuMulti(energyHatches) > 0;
    }

    private void acquire(MTEMultiBlockBase remote) {
        if (hosted.containsKey(remote)) return;
        IGregTechTileEntity tile = remote.getBaseMetaTileEntity();
        if (tile == null || remote.mMaxProgresstime > 0) return;
        String key = machineKey(remote);
        if (!tile.isAllowedToWork() && !persistedClaims.contains(key)) return;
        if (!HostedMachineRegistry.claim(remote, owner)) return;
        HostedJob restoredJob = pendingJobs.remove(key);
        if (restoredJob != null && restoredJob.isActive() && activeRemote != null) restoredJob = null;
        hosted.put(remote, restoredJob == null ? new HostedJob() : restoredJob);
        if (restoredJob != null && restoredJob.isActive()) activeRemote = remote;
        persistedClaims.add(key);
        tile.disableWorking();
        tile.setActive(false);
        MatterBlueprints.LOG.info("Hosting remote multiblock {} at {},{},{}", remote.mName, tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    private void release(MTEMultiBlockBase remote, Iterator<MTEMultiBlockBase> iterator) {
        HostedMachineRegistry.release(remote, owner);
        HostedJob job = hosted.get(remote);
        if (job != null) job.restoreToRemote(remote);
        if (remote == activeRemote) {
            activeRemote = null;
            setRemoteActivity(false);
        }
        iterator.remove();
        persistedClaims.remove(machineKey(remote));
        pendingJobs.remove(machineKey(remote));
        IGregTechTileEntity tile = remote.getBaseMetaTileEntity();
        if (tile != null && remote.isValid()) tile.enableWorking();
    }

    private String machineKey(MTEMultiBlockBase machine) {
        IGregTechTileEntity tile = machine.getBaseMetaTileEntity();
        if (tile == null || tile.getWorld() == null) return "invalid:" + System.identityHashCode(machine);
        return tile.getWorld().provider.dimensionId + ":" + tile.getXCoord() + ":" + tile.getYCoord() + ":" + tile.getZCoord();
    }

    private void startRecipe(MTEMultiBlockBase remote) {
        HatchSnapshot snapshot = new HatchSnapshot(remote);
        snapshot.useOwnerIO(owner, remote);
        AggregateCapacitySnapshot capacitySnapshot = new AggregateCapacitySnapshot(remote, hosted.keySet());
        boolean restoreBatchMode = remote.supportsBatchMode();
        boolean previousBatchMode = remote.isBatchModeEnabled();
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
                snapshot.restore(remote);
            }
        }
        if (successful) {
            HostedJob job = hosted.get(remote);
            if (job != null) {
                job.captureFromRemote(remote, hosted.size(), getCompletedRecipeCount(remote), getActualEnergyUsage(remote));
                activeRemote = remote;
                setRemoteActivity(true);
            }
        }
    }

    private void advanceRecipe(MTEMultiBlockBase remote, HostedJob job, long worldTick) {
        job.exposeProgressToRemote(remote);
        boolean continued = false;
        try {
            // Vanilla GT synchronizes the six maintenance flags from the hatch before runMachine().
            // Hosted machines do not execute their own runMachine(), so this must happen here as well.
            // The disabled remote still performs its own base maintenance synchronization. Refreshing here once per
            // second covers forks that skip it without repeating six hatch reads for every aggregate tick.
            if (worldTick % MAINTENANCE_REFRESH_TICKS == 0) remote.checkMaintenance();
            if (!remote.doRandomMaintenanceDamage()) return;
            long energyUsage = getActualEnergyUsage(remote);
            if (!drainCentralEnergy(energyUsage)) {
                statusKey = "matterblueprints.host.status.no_central_power";
                return;
            }
            if (hasCustomRunningTick(remote)) {
                EnergyHatchListSnapshot energyLists = new EnergyHatchListSnapshot(remote, hosted.keySet());
                RemoteEnergySnapshot energySnapshot = new RemoteEnergySnapshot(remote);
                try {
                    energySnapshot.stage(energyUsage);
                    if (!remote.onRunningTick(remote.getControllerSlot())) return;
                } finally {
                    energySnapshot.restore();
                    energyLists.restore(remote);
                }
            }
            if (!remote.polluteEnvironment(remote.getPollutionPerTick(remote.getControllerSlot()))) {
                remote.stopMachine();
                return;
            }
            invokeIncrementProgressTime(remote);
            job.progress = remote.mProgresstime;
            continued = true;
        } finally {
            boolean remoteStopped = remote.mMaxProgresstime <= 0;
            job.hideProgressFromRemote(remote);
            if (!continued && remoteStopped) job.clear();
        }
        if (!job.isActive() || job.progress < job.maxProgress) return;
        finishRecipe(remote, job);
    }

    private void finishRecipe(MTEMultiBlockBase remote, HostedJob job) {
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
        remote.recipesDone += getCompletedRecipeCount(remote);
        remote.setLastWorkingTick(remote.getTotalRunTime());
        job.clear();
        activeRemote = null;
        setRemoteActivity(false);
        if (!itemsAccepted || !fluidsAccepted) {
            statusKey = "matterblueprints.host.status.output_full";
        }
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
            if (output != null && !owner.addOutput(output)) accepted = false;
        }
        return accepted;
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
        centralEnergyHatches.addAll(owner.getExoticAndNormalEnergyHatchList());
        centralPowerCapacity = ExoticEnergyInputHelper.getTotalEuMulti(centralEnergyHatches);
    }

    private boolean drainCentralEnergy(long amount) {
        if (amount <= 0) return true;
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

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0 && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
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
        private final boolean hasCustomRunningTick;

        RemoteHooks(Class<?> type) {
            actualEnergyUsage = findLifecycleMethod(type, "getActualEnergyUsage");
            incrementProgressTime = findLifecycleMethod(type, "incrementProgressTime");
            outputAfterRecipe = findLifecycleMethod(type, "outputAfterRecipe");
            hasCustomRunningTick = declaresBeforeBase(type, "onRunningTick", ItemStack.class);
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

    /** Expands one representative machine's recipe calculation to the total capacity of the hosted machine bank. */
    private static final class AggregateCapacitySnapshot {

        private final ArrayList<MTEHatchEnergy> energyHatches;
        private final List<MTEHatch> exoticEnergyHatches;
        private final Object processingLogic;
        private final Supplier<Integer> maxParallelSupplier;
        private final int maxParallel;

        @SuppressWarnings("unchecked")
        AggregateCapacitySnapshot(MTEMultiBlockBase representative, Set<MTEMultiBlockBase> machines) {
            energyHatches = representative.mEnergyHatches;
            exoticEnergyHatches = representative.getExoticEnergyHatches();

            int totalParallel = 0;
            for (MTEMultiBlockBase machine : machines) {
                totalParallel = saturatingParallelAdd(totalParallel, Math.max(1, machine.getTrueParallel()));
            }
            final int aggregateParallel = Math.max(1, totalParallel);

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
                    setSupplier.invoke(logic, (Supplier<Integer>) () -> aggregateParallel);
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
            representative.mEnergyHatches = energyHatches;
            setExoticEnergyHatches(representative, exoticEnergyHatches);
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

        private static int saturatingParallelAdd(int left, int right) {
            if (left > Integer.MAX_VALUE - right) return Integer.MAX_VALUE;
            return left + right;
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

    /** Makes a custom running hook see every remote energy hatch while still restoring every stored-EU value. */
    private static final class EnergyHatchListSnapshot {

        private final ArrayList<MTEHatchEnergy> energyHatches;
        private final List<MTEHatch> exoticEnergyHatches;

        EnergyHatchListSnapshot(MTEMultiBlockBase representative, Set<MTEMultiBlockBase> machines) {
            energyHatches = representative.mEnergyHatches;
            exoticEnergyHatches = representative.getExoticEnergyHatches();
            ArrayList<MTEHatchEnergy> aggregateNormal = new ArrayList<MTEHatchEnergy>();
            List<MTEHatch> aggregateExotic = new ArrayList<MTEHatch>();
            for (MTEMultiBlockBase machine : machines) {
                aggregateNormal.addAll(machine.mEnergyHatches);
                aggregateExotic.addAll(machine.getExoticEnergyHatches());
            }
            representative.mEnergyHatches = aggregateNormal;
            setExoticEnergyHatches(representative, aggregateExotic);
        }

        void restore(MTEMultiBlockBase representative) {
            representative.mEnergyHatches = energyHatches;
            setExoticEnergyHatches(representative, exoticEnergyHatches);
        }
    }

    private static void setExoticEnergyHatches(MTEMultiBlockBase machine, List<MTEHatch> hatches) {
        try {
            Field field = MTEMultiBlockBase.class.getDeclaredField("mExoticEnergyHatches");
            field.setAccessible(true);
            field.set(machine, hatches);
        } catch (ReflectiveOperationException error) {
            MatterBlueprints.LOG.error("Could not swap exotic energy hatches for aggregation", error);
        }
    }

    /**
     * Lets the remote execute its normal running hook while billing the host. The original energy in every remote
     * hatch is restored afterwards, so those hatches define voltage/amperage but never supply the hosted recipe.
     */
    private static final class RemoteEnergySnapshot {

        private final List<EnergyEntry> entries = new ArrayList<EnergyEntry>();
        private long capacity;

        RemoteEnergySnapshot(MTEMultiBlockBase remote) {
            for (MTEHatch hatch : remote.getExoticAndNormalEnergyHatchList()) {
                if (hatch == null || hatch.getBaseMetaTileEntity() == null) continue;
                IGregTechTileEntity tile = hatch.getBaseMetaTileEntity();
                entries.add(new EnergyEntry(tile, tile.getStoredEU()));
                capacity = saturatingAdd(capacity, tile.getEUCapacity());
            }
        }

        boolean canStage(long amount) {
            return amount <= 0 || (!entries.isEmpty() && capacity >= amount);
        }

        void stage(long amount) {
            clearCurrentEnergy();
            long remaining = amount;
            for (EnergyEntry entry : entries) {
                if (remaining <= 0) break;
                long inserted = Math.min(remaining, entry.tile.getEUCapacity());
                if (inserted > 0 && entry.tile.increaseStoredEnergyUnits(inserted, false)) remaining -= inserted;
            }
            if (remaining > 0) {
                MatterBlueprints.LOG.error("Could not stage {} EU in remote energy hatches", amount);
            }
        }

        void restore() {
            clearCurrentEnergy();
            for (EnergyEntry entry : entries) {
                if (entry.stored > 0 && !entry.tile.increaseStoredEnergyUnits(entry.stored, false)) {
                    MatterBlueprints.LOG.error("Could not restore {} EU to a remote energy hatch", entry.stored);
                }
            }
        }

        private void clearCurrentEnergy() {
            for (EnergyEntry entry : entries) {
                long stored = entry.tile.getStoredEU();
                if (stored > 0) entry.tile.decreaseStoredEnergyUnits(stored, false);
            }
        }
    }

    private static final class EnergyEntry {

        private final IGregTechTileEntity tile;
        private final long stored;

        EnergyEntry(IGregTechTileEntity tile, long stored) {
            this.tile = tile;
            this.stored = stored;
        }
    }

    static final class HostedJob {

        private int progress;
        private int maxProgress;
        private ItemStack[] outputItems;
        private FluidStack[] outputFluids;
        private int machineCount;
        private int parallels;
        private long energyUsage;

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
            this.energyUsage = Math.max(0L, energyUsage);
            hideProgressFromRemote(remote);
        }

        void exposeProgressToRemote(MTEMultiBlockBase remote) {
            remote.mProgresstime = progress;
            remote.mMaxProgresstime = maxProgress;
        }

        void hideProgressFromRemote(MTEMultiBlockBase remote) {
            remote.mProgresstime = 0;
            remote.mMaxProgresstime = 0;
            remote.mOutputItems = null;
            remote.mOutputFluids = null;
        }

        void restoreToRemote(MTEMultiBlockBase remote) {
            if (!isActive()) return;
            remote.mProgresstime = progress;
            remote.mMaxProgresstime = maxProgress;
            remote.mOutputItems = outputItems;
            remote.mOutputFluids = outputFluids;
            clear();
        }

        void clear() {
            progress = 0;
            maxProgress = 0;
            outputItems = null;
            outputFluids = null;
            machineCount = 0;
            parallels = 0;
            energyUsage = 0;
        }

        void writeToNBT(NBTTagCompound tag) {
            tag.setInteger("progress", progress);
            tag.setInteger("maxProgress", maxProgress);
            tag.setInteger("machineCount", machineCount);
            tag.setInteger("parallels", parallels);
            tag.setLong("energyUsage", energyUsage);
            writeItems(tag, outputItems);
            writeFluids(tag, outputFluids);
        }

        static HostedJob readFromNBT(NBTTagCompound tag) {
            HostedJob job = new HostedJob();
            job.progress = Math.max(0, tag.getInteger("progress"));
            job.maxProgress = Math.max(0, tag.getInteger("maxProgress"));
            job.machineCount = Math.max(0, tag.getInteger("machineCount"));
            job.parallels = Math.max(0, tag.getInteger("parallels"));
            job.energyUsage = Math.max(0L, tag.getLong("energyUsage"));
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

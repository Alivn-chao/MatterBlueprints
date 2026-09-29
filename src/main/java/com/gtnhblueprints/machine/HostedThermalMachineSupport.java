package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.List;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Stages native thermal growth while the disabled remote is owned by the host. */
final class HostedThermalMachineSupport {
    private HostedThermalMachineSupport() {}

    static int kind(MTEMultiBlockBase machine) {
        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if (name.equals("gregtech.common.tileentities.machines.multi.MTEExothermicHearth")) return 1;
            if (name.equals("gregtech.common.tileentities.machines.multi.MTEEndothermicFridge")) return 2;
            if (name.equals("gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.mega.MTEMegaAlloyBlastSmelter")) return 3;
        }
        return 0;
    }

    static boolean matches(MTEMultiBlockBase representative, Iterable<MTEMultiBlockBase> machines) {
        int kind = kind(representative);
        String[] fields = kind == 1 ? new String[] { "coilLevel", "heatingCapacity", "glassTier", "isPyroSupplied" }
            : kind == 2 ? new String[] { "machineTier", "isCryoEnabled" }
                : new String[] { "coilLevel", "coilType", "glassTier", "speedBonus" };
        try {
            for (MTEMultiBlockBase machine : machines) {
                if (kind(machine) != kind || machine.getMaxInputVoltage() != representative.getMaxInputVoltage()) return false;
                for (String name : fields) {
                    if (!Objects.equals(field(machine, name).get(machine), field(representative, name).get(representative))) return false;
                }
            }
            return true;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot compare thermal machine configuration", error);
        }
    }

    static float boundedGrowth(float value, int kind, boolean unlocked) {
        float maximum = kind == 1 ? 2F : 1.5F;
        return unlocked ? maximum : Float.isNaN(value) ? 1F : Math.max(1F, Math.min(maximum, value));
    }

    static void idle(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job, long tick, boolean unlocked) {
        int kind = kind(remote);
        if (kind < 1 || kind > 2 || job.thermalGrowth < 0) return;
        if (unlocked) job.thermalGrowth = boundedGrowth(job.thermalGrowth, kind, true);
        else if (tick % 20 == 0) job.thermalGrowth = boundedGrowth(
            job.thermalGrowth - (kind == 1 ? .0027777778F : .025000002F), kind, false);
    }

    static Scope open(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job, boolean unlocked) {
        return open(remote, job, unlocked, 1D);
    }

    static Scope open(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job, boolean unlocked,
        double durationMultiplier) {
        return new Scope(remote, job, unlocked, durationMultiplier);
    }

    static void release(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job) {
        int kind = kind(remote);
        if (kind < 1 || kind > 2 || job == null || job.thermalGrowth < 0) return;
        try {
            field(remote, kind == 1 ? "parallelModifier" : "speedBoost").setFloat(remote, job.thermalGrowth);
            field(remote, "runningTickCounter").setInt(remote, job.thermalTicks);
            if (kind == 2 && job.thermalBooster >= -1) {
                List<?> boosters = (List<?>) field(remote, "BOOSTER_FLUIDS").get(null);
                field(remote, "currentBoosterFluid").set(remote,
                    job.thermalBooster >= 0 && job.thermalBooster < boosters.size() ? boosters.get(job.thermalBooster) : null);
            }
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot return thermal growth", error);
        }
    }

    private static Field field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new IllegalStateException("Unsupported thermal machine field: " + name);
    }

    static final class Scope implements AutoCloseable {
        private final MTEMultiBlockBase remote;
        private final HostedMachineCoordinator.HostedJob job;
        private final boolean unlocked;
        private final int kind;
        private Field growth, ticks, enabled;
        private float previousGrowth;
        private int previousTicks;
        private boolean previousEnabled;
        private Field booster;
        private Object previousBooster;
        private List<?> boosters;
        private Field alloySpeed;
        private double previousAlloySpeed;

        Scope(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job, boolean unlocked,
            double durationMultiplier) {
            this.remote = remote;
            this.job = job;
            this.unlocked = unlocked;
            kind = kind(remote);
            if (kind == 3) {
                try {
                    // MABS overwrites ProcessingLogic's speed with this machine field in its calculator.
                    alloySpeed = field(remote, "speedBonus");
                    previousAlloySpeed = alloySpeed.getDouble(remote);
                    alloySpeed.setDouble(remote, previousAlloySpeed * durationMultiplier);
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException("Cannot stage alloy smelter speed", error);
                }
            }
            if (kind < 1 || kind > 2) return;
            try {
                growth = field(remote, kind == 1 ? "parallelModifier" : "speedBoost");
                ticks = field(remote, "runningTickCounter");
                enabled = field(remote, kind == 1 ? "isPyroSupplied" : "isCryoEnabled");
                previousGrowth = growth.getFloat(remote);
                previousTicks = ticks.getInt(remote);
                previousEnabled = enabled.getBoolean(remote);
                if (kind == 2) {
                    booster = field(remote, "currentBoosterFluid");
                    previousBooster = booster.get(remote);
                    boosters = (List<?>) field(remote, "BOOSTER_FLUIDS").get(null);
                    if (job.thermalBooster == -2) job.thermalBooster = boosters.indexOf(previousBooster);
                    booster.set(remote, job.thermalBooster >= 0 && job.thermalBooster < boosters.size()
                        ? boosters.get(job.thermalBooster) : null);
                }
                if (job.thermalGrowth < 0) {
                    job.thermalGrowth = boundedGrowth(previousGrowth, kind, false);
                    job.thermalTicks = previousTicks;
                }
                growth.setFloat(remote, boundedGrowth(job.thermalGrowth, kind, unlocked));
                ticks.setInt(remote, job.thermalTicks);
                if (unlocked) enabled.setBoolean(remote, false);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot stage thermal growth", error);
            }
        }

        public void close() {
            if (alloySpeed != null) {
                try {
                    alloySpeed.setDouble(remote, previousAlloySpeed);
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException("Cannot restore alloy smelter speed", error);
                }
            }
            if (growth == null) return;
            try {
                job.thermalGrowth = boundedGrowth(growth.getFloat(remote), kind, unlocked);
                job.thermalTicks = ticks.getInt(remote);
                if (booster != null) {
                    job.thermalBooster = boosters.indexOf(booster.get(remote));
                    booster.set(remote, previousBooster);
                }
                growth.setFloat(remote, previousGrowth);
                ticks.setInt(remote, previousTicks);
                enabled.setBoolean(remote, previousEnabled);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot restore thermal growth", error);
            }
        }
    }
}

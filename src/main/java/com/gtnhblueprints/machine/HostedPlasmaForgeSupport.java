package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.math.BigInteger;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;

/** Reuses the forge's fuel adjustment, heat validation and convergence/catalyst checks. */
final class HostedPlasmaForgeSupport {

    private static final Field RUNTIME = field("running_time");
    private static final Field DISCOUNT = field("discount");
    private static final Field HEAT = field("mHeatingCapacity");
    private static final Field CONVERGENCE = field("convergence");
    private static final Field CATALYST = field("catalystTypeForRecipesWithoutCatalyst");
    private static final long FULL_DISCOUNT_TICKS = 576000L;

    private HostedPlasmaForgeSupport() {}

    static boolean isPlasmaForge(MTEMultiBlockBase machine) {
        return machine instanceof MTEPlasmaForge;
    }

    static boolean matches(MTEMultiBlockBase representative, Iterable<MTEMultiBlockBase> machines) {
        try {
            for (MTEMultiBlockBase machine : machines) {
                if (!isPlasmaForge(machine) || HEAT.getInt(machine) != HEAT.getInt(representative)
                    || CONVERGENCE.getBoolean(machine) != CONVERGENCE.getBoolean(representative)
                    || CATALYST.getInt(machine) != CATALYST.getInt(representative)
                    || machine.getMaxInputVoltage() != representative.getMaxInputVoltage()) return false;
            }
            return true;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read plasma forge configuration", error);
        }
    }

    static Scope open(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job, long effectiveRuntime) {
        return new Scope(remote, job, effectiveRuntime);
    }

    /** Map the authoritative host clock onto GT's native eight-hour discount curve. */
    static long nativeRuntime(long productiveTicks, long targetTicks, boolean unlocked) {
        if (unlocked || productiveTicks >= Math.max(1L, targetTicks)) return FULL_DISCOUNT_TICKS;
        return BigInteger.valueOf(Math.max(0L, productiveTicks)).multiply(BigInteger.valueOf(FULL_DISCOUNT_TICKS))
            .divide(BigInteger.valueOf(Math.max(1L, targetTicks))).longValue();
    }

    static double fuelMultiplier(long runtime) {
        return 1D - 0.5D * Math.min(FULL_DISCOUNT_TICKS, Math.max(0L, runtime)) / FULL_DISCOUNT_TICKS;
    }

    static void release(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job) {
        if (!isPlasmaForge(remote) || job == null || job.plasmaRuntime < 0) return;
        try {
            RUNTIME.setLong(remote, job.plasmaRuntime);
            DISCOUNT.setDouble(remote, fuelMultiplier(job.plasmaRuntime));
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot return plasma forge warm-up state", error);
        }
    }

    private static Field field(String name) {
        try {
            Field field = MTEPlasmaForge.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported plasma forge field: " + name, error);
        }
    }

    static final class Scope implements AutoCloseable {

        private final MTEMultiBlockBase remote;
        private final long runtime;
        private final double discount;
        private final boolean recipeLocked;

        Scope(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job, long effectiveRuntime) {
            this.remote = remote;
            recipeLocked = remote.mLockedToSingleRecipe;
            try {
                runtime = RUNTIME.getLong(remote);
                discount = DISCOUNT.getDouble(remote);
                // Old per-job/native counters can contain speculative recipe duration. Never import them.
                job.plasmaRuntime = Math.min(FULL_DISCOUNT_TICKS, Math.max(0L, effectiveRuntime));
                RUNTIME.setLong(remote, job.plasmaRuntime);
                DISCOUNT.setDouble(remote, fuelMultiplier(job.plasmaRuntime));
                // SingleRecipeCheck stores fixed input costs (and an adjusted recipe). It cannot be reused for
                // a changing catalyst price. Native matching/ParallelHelper must recalculate each hosted batch.
                remote.mLockedToSingleRecipe = false;
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot stage plasma forge fuel discount", error);
            }
        }

        @Override
        public void close() {
            try {
                // Native checkProcessing credits mMaxProgresstime immediately. Discard that speculative credit;
                // only the authoritative productive host clock determines the next batch's price.
                RUNTIME.setLong(remote, runtime);
                DISCOUNT.setDouble(remote, discount);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot restore plasma forge fuel state", error);
            } finally {
                remote.mLockedToSingleRecipe = recipeLocked;
            }
        }
    }
}

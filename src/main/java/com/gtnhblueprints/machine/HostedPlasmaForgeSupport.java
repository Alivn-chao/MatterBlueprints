package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.ItemList;
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
        CohortKey key = configuration(representative);
        for (MTEMultiBlockBase machine : machines) if (!isPlasmaForge(machine) || !key.equals(configuration(machine))) return false;
        return true;
    }

    static boolean hasMatrix(MTEMultiBlockBase machine) {
        ItemStack stack = machine.getControllerSlot();
        return stack != null && stack.stackSize > 0 && ItemList.Transdimensional_Alignment_Matrix.hasBeenSet()
            && isMatrix(stack, ItemList.Transdimensional_Alignment_Matrix.get(1));
    }

    static boolean isMatrix(ItemStack stack, ItemStack matrix) {
        return stack != null && stack.stackSize > 0 && matrix != null && stack.isItemEqual(matrix);
    }

    static CohortKey configuration(MTEMultiBlockBase machine) {
        try {
            boolean matrix = hasMatrix(machine);
            return new CohortKey(matrix, HEAT.getInt(machine), CATALYST.getInt(machine),
                !matrix && CONVERGENCE.getBoolean(machine), machine.getMaxInputVoltage(),
                gregtech.api.util.ExoticEnergyInputHelper.getTotalEuMulti(machine.getExoticAndNormalEnergyHatchList()));
        } catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read plasma forge configuration", error); }
    }

    static final class CohortKey {
        final boolean matrix;
        private final int heat, catalyst;
        private final boolean convergence;
        private final long voltage, power;

        CohortKey(boolean matrix, int heat, int catalyst, boolean convergence, long voltage, long power) {
            this.matrix = matrix;
            this.heat = heat;
            this.catalyst = catalyst;
            this.convergence = convergence;
            this.voltage = voltage;
            this.power = power;
        }
        @Override public boolean equals(Object object) {
            if (!(object instanceof CohortKey)) return false;
            CohortKey other = (CohortKey) object;
            return matrix == other.matrix && heat == other.heat && catalyst == other.catalyst
                && convergence == other.convergence && voltage == other.voltage && power == other.power;
        }
        @Override public int hashCode() { return Objects.hash(matrix, heat, catalyst, convergence, voltage, power); }
    }

    static Reservations reservations(Iterable<HostedMachineCoordinator.HostedJob> jobs) {
        Reservations result = new Reservations();
        for (HostedMachineCoordinator.HostedJob job : jobs) {
            if (!job.isActive()) continue;
            if (job.plasmaMembers.isEmpty()) result.legacyBank = true;
            result.members.addAll(job.plasmaMembers);
        }
        return result;
    }

    static final class Reservations {
        private boolean legacyBank;
        private final Set<String> members = new HashSet<>();
        boolean contains(String member) { return legacyBank || members.contains(member); }
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
        private final boolean convergence;

        Scope(MTEMultiBlockBase remote, HostedMachineCoordinator.HostedJob job, long effectiveRuntime) {
            this.remote = remote;
            recipeLocked = remote.mLockedToSingleRecipe;
            try {
                runtime = RUNTIME.getLong(remote);
                discount = DISCOUNT.getDouble(remote);
                convergence = CONVERGENCE.getBoolean(remote);
                job.plasmaMatrix = hasMatrix(remote);
                // Hosted matrix OC is supplied by ProcessingLogic, independently of native convergence.
                // Keep native heat checks and discounted base catalyst, but suppress the surcharge path.
                if (job.plasmaMatrix) CONVERGENCE.setBoolean(remote, false);
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
                CONVERGENCE.setBoolean(remote, convergence);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot restore plasma forge fuel state", error);
            } finally {
                remote.mLockedToSingleRecipe = recipeLocked;
            }
        }
    }
}

package com.gtnhblueprints.machine;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import net.minecraftforge.fluids.FluidStack;

import goodgenerator.blocks.tileEntity.AntimatterForge;
import goodgenerator.blocks.tileEntity.AntimatterOutputHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Routes native cycle and always-on containment withdrawals to exactly one host. */
public final class HostedAntimatterSupport {

    public interface Patched {
        List<AntimatterOutputHatch> matterblueprints$getPhysicalHatches();
        long matterblueprints$getRollingCost();
        float matterblueprints$getMagneticModifier();
        void matterblueprints$restoreContainment(long rollingCost, float magneticModifier);
    }

    @FunctionalInterface
    interface Power {
        boolean drain(long amount, boolean cycle);
    }

    private static final Map<MTEMultiBlockBase, Binding> BINDINGS = new IdentityHashMap<>();

    private static final class Binding {
        final Power power;
        final Supplier<List<AntimatterOutputHatch>> hatches;
        final IntSupplier machines;
        final LongConsumer spill;
        Binding(Power power, Supplier<List<AntimatterOutputHatch>> hatches, IntSupplier machines, LongConsumer spill) {
            this.power = power;
            this.hatches = hatches;
            this.machines = machines;
            this.spill = spill;
        }
    }

    private HostedAntimatterSupport() {}

    static boolean isAntimatter(MTEMultiBlockBase machine) {
        return machine instanceof AntimatterForge;
    }

    static boolean attach(MTEMultiBlockBase machine, Power power, Supplier<List<AntimatterOutputHatch>> hatches,
        IntSupplier machines, LongConsumer spill) {
        if (!(machine instanceof Patched)) return false;
        BINDINGS.put(machine, new Binding(power, hatches, machines, spill));
        return true;
    }

    static boolean physicalHatchesEmpty(MTEMultiBlockBase machine) {
        if (!(machine instanceof Patched)) return false;
        for (AntimatterOutputHatch hatch : ((Patched) machine).matterblueprints$getPhysicalHatches()) {
            if (hatch != null && hatch.getFluid() != null && hatch.getFluid().amount > 0) return false;
        }
        return true;
    }

    static void release(MTEMultiBlockBase machine) {
        BINDINGS.remove(machine);
    }

    /** Called by the mixin on the server thread, also when the native controller is disabled. */
    public static boolean drain(AntimatterForge machine, long amount, boolean cycle) {
        Binding binding = BINDINGS.get(machine);
        return binding == null ? machine.drainEnergyInput(amount) : binding.power.drain(amount, cycle);
    }

    /** Structure checks still see physical empty hatches; only production and containment see the host's bank. */
    public static List<AntimatterOutputHatch> hatches(AntimatterForge machine, List<AntimatterOutputHatch> physical) {
        Binding binding = BINDINGS.get(machine);
        return binding == null ? physical : binding.hatches.get();
    }

    public static boolean isHosted(AntimatterForge forge) { return BINDINGS.containsKey(forge); }

    public static int machines(AntimatterForge machine) {
        Binding binding = BINDINGS.get(machine);
        return binding == null ? 1 : Math.max(1, binding.machines.getAsInt());
    }

    public static int fill(AntimatterForge machine, AntimatterOutputHatch hatch, FluidStack fluid, boolean commit) {
        int accepted = hatch.fill(fluid, commit);
        Binding binding = BINDINGS.get(machine);
        if (binding != null && commit && fluid.amount > accepted) {
            // Kept private to this transaction until native protomatter consumption succeeds.
            binding.spill.accept((long) fluid.amount - accepted);
            return fluid.amount;
        }
        return accepted;
    }
}

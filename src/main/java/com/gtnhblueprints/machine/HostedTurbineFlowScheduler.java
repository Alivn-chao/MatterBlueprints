package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhblueprints.MatterBlueprints;

import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchMultiInput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.tileentities.machines.multi.turbines.MTELargeTurbineBase;
import gregtech.common.tileentities.machines.multi.xlturbines.MTEXLTurbineBase;

/**
 * Allocates a shared central fluid supply to physical hosted turbines. Turbines below their optimal flow are not
 * started; once every turbine can reach its optimum, normal large turbines receive efficient overflow by marginal
 * EU/L. The original turbine checkProcessing implementation remains responsible for consuming the quota and
 * calculating output.
 */
final class HostedTurbineFlowScheduler {

    private static final Field LARGE_TURBINE_LOOSE_FIT = resolveLargeTurbineLooseFit();

    private HostedTurbineFlowScheduler() {}

    static boolean isTurbine(MTEMultiBlockBase remote) {
        return remote instanceof MTELargeTurbineBase || remote instanceof MTEXLTurbineBase;
    }

    static Map<MTEMultiBlockBase, FlowPlan> plan(MTEHostedMachineController owner,
        Iterable<MTEMultiBlockBase> remotes) {
        CentralFluids central = CentralFluids.read(owner);
        if (central.amounts.isEmpty()) return Collections.emptyMap();

        Map<Fluid, List<TurbineFlow>> groups = new LinkedHashMap<Fluid, List<TurbineFlow>>();
        for (MTEMultiBlockBase remote : remotes) {
            TurbineFlow flow = TurbineFlow.create(remote, central.amounts);
            if (flow == null) continue;
            List<TurbineFlow> group = groups.get(flow.fluid);
            if (group == null) {
                group = new ArrayList<TurbineFlow>();
                groups.put(flow.fluid, group);
            }
            group.add(flow);
        }

        Map<MTEMultiBlockBase, FlowPlan> plans = new IdentityHashMap<MTEMultiBlockBase, FlowPlan>();
        for (Map.Entry<Fluid, List<TurbineFlow>> entry : groups.entrySet()) {
            long available = central.amounts.get(entry.getKey()).longValue();
            allocate(entry.getValue(), available, plans);
        }
        return plans;
    }

    private static void allocate(List<TurbineFlow> turbines, long available,
        Map<MTEMultiBlockBase, FlowPlan> plans) {
        Collections.sort(turbines, new Comparator<TurbineFlow>() {

            @Override
            public int compare(TurbineFlow left, TurbineFlow right) {
                int efficiency = Double.compare(right.powerAtOptimal / right.optimal, left.powerAtOptimal / left.optimal);
                if (efficiency != 0) return efficiency;
                return left.key.compareTo(right.key);
            }
        });

        long allOptimal = 0;
        for (TurbineFlow turbine : turbines) allOptimal = saturatingAdd(allOptimal, turbine.optimal);

        if (available < allOptimal) {
            // Running fewer complete turbines produces more power than starving every rotor below its optimum.
            for (TurbineFlow turbine : turbines) {
                if (available < turbine.optimal) continue;
                plans.put(turbine.remote, new FlowPlan(turbine.fluid, turbine.optimal));
                available -= turbine.optimal;
            }
            return;
        }

        long target = allOptimal;
        long allMaximum = 0;
        for (TurbineFlow turbine : turbines) allMaximum = saturatingAdd(allMaximum, turbine.maximum);
        target = Math.min(available, allMaximum);

        if (target <= allOptimal) {
            for (TurbineFlow turbine : turbines) {
                plans.put(turbine.remote, new FlowPlan(turbine.fluid, turbine.optimal));
            }
            return;
        }

        // Water-fill the concave overflow curves. This puts each extra litre where it yields the most additional EU,
        // rather than overfeeding the first turbine in map iteration order.
        double low = 0.0D;
        double high = 0.0D;
        for (TurbineFlow turbine : turbines) high = Math.max(high, turbine.marginal(turbine.optimal));
        for (int iteration = 0; iteration < 56; iteration++) {
            double middle = (low + high) * 0.5D;
            double total = 0.0D;
            for (TurbineFlow turbine : turbines) total += turbine.flowAtMarginal(middle);
            if (total > target) low = middle;
            else high = middle;
        }

        long assigned = 0;
        for (TurbineFlow turbine : turbines) {
            turbine.assigned = clamp((long) Math.floor(turbine.flowAtMarginal(high)), turbine.optimal, turbine.maximum);
            assigned += turbine.assigned;
        }
        while (assigned < target) {
            TurbineFlow best = null;
            double bestGain = Double.NEGATIVE_INFINITY;
            for (TurbineFlow turbine : turbines) {
                if (turbine.assigned >= turbine.maximum) continue;
                double gain = turbine.power(turbine.assigned + 1) - turbine.power(turbine.assigned);
                if (gain > bestGain) {
                    bestGain = gain;
                    best = turbine;
                }
            }
            if (best == null || bestGain <= 0.0D) break;
            best.assigned++;
            assigned++;
        }
        for (TurbineFlow turbine : turbines) {
            plans.put(turbine.remote, new FlowPlan(turbine.fluid, turbine.assigned));
        }
    }

    /** Applies a quota to the live stacks returned by GT, including ME hatch transaction stacks. */
    static FluidQuota restrict(MTEMultiBlockBase remote, FlowPlan plan) {
        return plan == null ? FluidQuota.NONE : new FluidQuota(remote, plan.fluid, plan.amount);
    }

    static final class FlowPlan {

        final Fluid fluid;
        final long amount;

        FlowPlan(Fluid fluid, long amount) {
            this.fluid = fluid;
            this.amount = amount;
        }
    }

    static final class FluidQuota {

        static final FluidQuota NONE = new FluidQuota();

        private final IdentityHashMap<FluidStack, Integer> withheld;
        private final List<FluidBinding> bindings;

        private FluidQuota() {
            withheld = null;
            bindings = null;
        }

        FluidQuota(MTEMultiBlockBase remote, Fluid selected, long quota) {
            withheld = new IdentityHashMap<FluidStack, Integer>();
            bindings = findBindings(remote);
            long remaining = Math.max(0L, quota);
            for (FluidStack stack : remote.getStoredFluids()) {
                if (stack == null || withheld.containsKey(stack)) continue;
                int original = Math.max(0, stack.amount);
                int visible = 0;
                if (stack.getFluid() == selected && remaining > 0) {
                    visible = (int) Math.min((long) original, remaining);
                    remaining -= visible;
                }
                withheld.put(stack, Integer.valueOf(original - visible));
                stack.amount = visible;
            }
        }

        void restore() {
            if (withheld == null) return;
            for (Map.Entry<FluidStack, Integer> entry : withheld.entrySet()) {
                entry.getKey().amount += entry.getValue().intValue();
            }
            // A normal or multi-input tank detaches a stack when the visible quota reaches zero. Reattach the restored
            // reference so withheld fluid cannot disappear when a turbine consumes its exact allocation.
            for (FluidBinding binding : bindings) binding.reattach();
        }

        private static List<FluidBinding> findBindings(MTEMultiBlockBase remote) {
            List<FluidBinding> result = new ArrayList<FluidBinding>();
            for (MTEHatchInput hatch : remote.mInputHatches) {
                if (hatch == null) continue;
                if (hatch instanceof MTEHatchMultiInput) {
                    MTEHatchMultiInput multi = (MTEHatchMultiInput) hatch;
                    FluidStack[] stored = multi.getStoredFluid();
                    for (int slot = 0; slot < stored.length; slot++) {
                        if (stored[slot] != null) result.add(new FluidBinding(multi, slot, stored[slot]));
                    }
                } else if (hatch.getFillableStack() != null) {
                    result.add(new FluidBinding(hatch, hatch.getFillableStack()));
                }
            }
            return result;
        }
    }

    private static final class FluidBinding {

        private final MTEHatchInput hatch;
        private final MTEHatchMultiInput multi;
        private final int slot;
        private final FluidStack stack;

        FluidBinding(MTEHatchInput hatch, FluidStack stack) {
            this.hatch = hatch;
            this.multi = null;
            this.slot = -1;
            this.stack = stack;
        }

        FluidBinding(MTEHatchMultiInput multi, int slot, FluidStack stack) {
            this.hatch = null;
            this.multi = multi;
            this.slot = slot;
            this.stack = stack;
        }

        void reattach() {
            if (multi != null) {
                if (multi.getFluid(slot) != stack) multi.setFluid(stack, slot);
            } else if (hatch.getFillableStack() != stack) {
                hatch.setFillableStack(stack);
            }
        }
    }

    private static final class CentralFluids {

        final LinkedHashMap<Fluid, Long> amounts = new LinkedHashMap<Fluid, Long>();

        static CentralFluids read(MTEHostedMachineController owner) {
            CentralFluids result = new CentralFluids();
            owner.startRecipeProcessing();
            try {
                for (FluidStack stack : owner.getStoredFluids()) {
                    if (stack == null || stack.amount <= 0) continue;
                    Long previous = result.amounts.get(stack.getFluid());
                    result.amounts.put(
                        stack.getFluid(),
                        Long.valueOf(saturatingAdd(previous == null ? 0L : previous.longValue(), stack.amount)));
                }
            } catch (RuntimeException error) {
                MatterBlueprints.LOG.warn("Could not inspect central turbine fluids", error);
            } finally {
                owner.endRecipeProcessing();
            }
            return result;
        }
    }

    private static final class TurbineFlow {

        final MTEMultiBlockBase remote;
        final Fluid fluid;
        final String key;
        final long optimal;
        final long maximum;
        final double coefficient;
        final double overflowDenominator;
        final double powerAtOptimal;
        final long powerCap;
        long assigned;

        TurbineFlow(MTEMultiBlockBase remote, Fluid fluid, long optimal, long maximum, double coefficient,
            double overflowDenominator, long powerCap) {
            this.remote = remote;
            this.fluid = fluid;
            this.key = machineKey(remote);
            this.optimal = Math.max(1L, optimal);
            this.coefficient = Math.max(0.0D, coefficient);
            this.overflowDenominator = Math.max(1.0D, overflowDenominator);
            this.powerCap = Math.max(0L, powerCap);
            this.powerAtOptimal = power(this.optimal);
            this.maximum = findUsefulMaximum(Math.max(this.optimal, maximum));
        }

        static TurbineFlow create(MTEMultiBlockBase remote, LinkedHashMap<Fluid, Long> available) {
            ItemStack rotor = findRotor(remote);
            if (!(rotor != null && rotor.getItem() instanceof MetaGeneratedTool)) return null;
            TurbineStatCalculator stats = new TurbineStatCalculator((MetaGeneratedTool) rotor.getItem(), rotor);
            boolean xl = remote instanceof MTEXLTurbineBase;
            int speed = xl ? ((MTEXLTurbineBase) remote).getSpeedMultiplier() : 1;
            boolean loose = xl ? ((MTEXLTurbineBase) remote).isLooseMode() : isLoose(remote);
            String className = remote.getClass().getSimpleName().toLowerCase(Locale.ROOT);

            if (className.contains("gas")) {
                FluidStack fuel = findFuel(available, false);
                if (fuel == null) return null;
                int value = getFuelValue(fuel, false);
                if (value <= 0) return null;
                double optimalEnergy = loose ? stats.getOptimalLooseGasEUt() : stats.getOptimalGasEUt();
                long optimal = optimalEnergy < value ? 1L
                    : Math.max(1L, (long) ((loose ? stats.getOptimalLooseGasFlow() : stats.getOptimalGasFlow()) * speed
                        / value));
                double coefficient = optimalEnergy < value ? optimalEnergy
                    : value * (loose ? stats.getLooseGasEfficiency() : stats.getGasEfficiency());
                int overflow = stats.getOverflowEfficiency();
                double denominator = xl ? 1.0D : (overflow * 3.0D) - 1.0D;
                long maximum = xl ? optimal : floorPositive(optimal * 1.5D * overflow);
                return new TurbineFlow(remote, fuel.getFluid(), optimal, maximum, coefficient, denominator,
                    HostedMachineCoordinator.getDynamoCapacity(remote));
            }

            if (className.contains("plasma")) {
                FluidStack fuel = findFuel(available, true);
                if (fuel == null) return null;
                int value = getFuelValue(fuel, true);
                if (value <= 0) return null;
                double baseFlow = loose ? stats.getOptimalLoosePlasmaFlow() : stats.getOptimalPlasmaFlow();
                long optimal = Math.max(1L, (long) Math.ceil(baseFlow * speed * 20.0D / value));
                double coefficient = (value / 20.0D)
                    * (loose ? stats.getLoosePlasmaEfficiency() : stats.getPlasmaEfficiency());
                int overflow = stats.getOverflowEfficiency();
                double denominator = xl ? 1.0D : (overflow * 3.0D) + 1.0D;
                long maximum = xl ? optimal : floorPositive(optimal * (1.5D * overflow + 1.0D));
                return new TurbineFlow(remote, fuel.getFluid(), optimal, maximum, coefficient, denominator,
                    HostedMachineCoordinator.getDynamoCapacity(remote));
            }

            FluidStack steam = findSteam(available, className, xl);
            if (steam == null) return null;
            double effectiveOptimal = speed
                * (loose ? stats.getOptimalLooseSteamFlow() : stats.getOptimalSteamFlow());
            boolean dense = isDenseSteam(steam);
            double physicalScale = dense ? 1000.0D : 1.0D;
            long optimal = Math.max(1L, (long) Math.ceil(effectiveOptimal / physicalScale));
            double coefficient = (isHighPressureSteam(className) ? 1.0D : 0.5D) * physicalScale
                * (loose ? stats.getLooseSteamEfficiency() : stats.getSteamEfficiency());
            int overflow = stats.getOverflowEfficiency();
            double denominator = xl ? 1.0D : overflow + 1.0D;
            long maximum = xl ? optimal
                : Math.max(optimal, (long) Math.ceil(effectiveOptimal * (1.0D + 0.5D * overflow) / physicalScale));
            return new TurbineFlow(remote, steam.getFluid(), optimal, maximum, coefficient, denominator,
                HostedMachineCoordinator.getDynamoCapacity(remote));
        }

        private long findUsefulMaximum(long requestedMaximum) {
            if (requestedMaximum <= optimal || powerCap <= powerAtOptimal) return optimal;
            long low = optimal;
            long high = requestedMaximum;
            while (low < high) {
                long middle = low + (high - low + 1L) / 2L;
                if (rawPower(middle) <= powerCap) low = middle;
                else high = middle - 1L;
            }
            return Math.max(optimal, low);
        }

        double power(long flow) {
            return Math.min(powerCap, rawPower(flow));
        }

        private double rawPower(double flow) {
            double efficiency = 1.0D
                - Math.max(0.0D, flow - optimal) / (optimal * overflowDenominator);
            return Math.max(0.0D, coefficient * flow * efficiency);
        }

        double marginal(long flow) {
            if (flow >= maximum || rawPower(flow) >= powerCap) return 0.0D;
            return coefficient
                * ((overflowDenominator + 1.0D) / overflowDenominator
                    - 2.0D * flow / (optimal * overflowDenominator));
        }

        double flowAtMarginal(double marginal) {
            if (maximum <= optimal || coefficient <= 0.0D) return optimal;
            double flow = optimal * 0.5D
                * (overflowDenominator + 1.0D - marginal * overflowDenominator / coefficient);
            return Math.max(optimal, Math.min(maximum, flow));
        }
    }

    private static ItemStack findRotor(MTEMultiBlockBase remote) {
        if (remote instanceof MTEXLTurbineBase) {
            MTEXLTurbineBase xl = (MTEXLTurbineBase) remote;
            for (int slot = 0; slot < xl.turbineHolder.getSlots(); slot++) {
                ItemStack rotor = xl.turbineHolder.getStackInSlot(slot);
                if (MTEXLTurbineBase.isValidTurbine(rotor)) return rotor;
            }
            return null;
        }
        return remote.getControllerSlot();
    }

    private static FluidStack findFuel(LinkedHashMap<Fluid, Long> available, boolean plasma) {
        for (Map.Entry<Fluid, Long> entry : available.entrySet()) {
            if (entry.getValue().longValue() <= 0) continue;
            FluidStack stack = new FluidStack(entry.getKey(), 1);
            if (getFuelValue(stack, plasma) > 0) return stack;
        }
        return null;
    }

    private static int getFuelValue(FluidStack stack, boolean plasma) {
        GTRecipe recipe = (plasma ? RecipeMaps.plasmaFuels : RecipeMaps.gasTurbineFuels).getBackend()
            .findFuel(stack);
        return recipe == null ? 0 : recipe.mSpecialValue;
    }

    private static FluidStack findSteam(LinkedHashMap<Fluid, Long> available, String className, boolean xl) {
        for (Map.Entry<Fluid, Long> entry : available.entrySet()) {
            if (entry.getValue().longValue() <= 0) continue;
            FluidStack stack = new FluidStack(entry.getKey(), 1);
            String name = stack.getFluid().getUnlocalizedName(stack);
            boolean accepted;
            if (!xl) {
                accepted = GTModHandler.isAnySteam(stack);
            } else if (className.contains("hpsteam")) {
                accepted = "ic2.fluidSuperheatedSteam".equals(name) || "fluid.densesuperheatedsteam".equals(name);
            } else if (className.contains("scsteam")) {
                accepted = "fluid.supercriticalsteam".equals(name) || "fluid.densesupercriticalsteam".equals(name)
                    || "supercriticalsteam".equals(stack.getFluid().getName());
            } else {
                accepted = "fluid.steam".equals(name) || "ic2.fluidSteam".equals(name)
                    || "fluid.mfr.steam.still.name".equals(name) || "fluid.densesteam".equals(name);
            }
            if (accepted) return stack;
        }
        return null;
    }

    private static boolean isHighPressureSteam(String className) {
        return className.contains("hpsteam") || className.contains("scsteam");
    }

    private static boolean isDenseSteam(FluidStack stack) {
        String name = stack.getFluid().getUnlocalizedName(stack);
        return name != null && name.toLowerCase(Locale.ROOT).contains("dense");
    }

    private static boolean isLoose(MTEMultiBlockBase remote) {
        if (LARGE_TURBINE_LOOSE_FIT == null) return false;
        try {
            return LARGE_TURBINE_LOOSE_FIT.getBoolean(remote);
        } catch (IllegalAccessException error) {
            return false;
        }
    }

    private static Field resolveLargeTurbineLooseFit() {
        try {
            Field field = MTELargeTurbineBase.class.getDeclaredField("looseFit");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            MatterBlueprints.LOG.warn("Could not resolve large turbine fitting field", error);
            return null;
        }
    }

    private static String machineKey(MTEMultiBlockBase remote) {
        if (remote.getBaseMetaTileEntity() == null) return remote.mName;
        return remote.getBaseMetaTileEntity().getXCoord() + ":" + remote.getBaseMetaTileEntity().getYCoord() + ":"
            + remote.getBaseMetaTileEntity().getZCoord();
    }

    private static long floorPositive(double value) {
        if (Double.isNaN(value) || value <= 1.0D) return 1L;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.floor(value);
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}

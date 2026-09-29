package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.LongPredicate;
import java.util.function.Consumer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.tileentity.IOverclockDescriptionProvider;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.common.tileentities.machines.multi.MTEFusionComputer;
import goodgenerator.blocks.tileEntity.base.MTELargeFusionComputer;

/** Keeps native fusion tier/OC rules, with transactional, once-per-binding ignition. */
final class HostedFusionSupport {
    private static final Field PROCESSING = field(MTEMultiBlockBase.class, "processingLogic");

    static boolean isFusion(MTEMultiBlockBase machine) {
        return machine instanceof MTEFusionComputer || machine instanceof MTELargeFusionComputer;
    }

    static int capacity(MTEMultiBlockBase machine) {
        if (machine instanceof MTELargeFusionComputer) {
            MTELargeFusionComputer fusion = (MTELargeFusionComputer) machine;
            return parallelProduct(fusion.getMaxPara(), fusion.extraPara(100L));
        }
        return Math.max(1, machine.getTrueParallel());
    }

    static int parallelProduct(int first, int second) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) first * second));
    }

    static long aggregateAmperage(long singleAmperage, int machines) {
        return singleAmperage > Long.MAX_VALUE / Math.max(1, machines)
            ? Long.MAX_VALUE : singleAmperage * Math.max(1, machines);
    }

    static boolean flushFluids(FluidStack[] outputs, Consumer<FluidStack> eject) {
        boolean accepted = true;
        if (outputs != null) for (FluidStack output : outputs) {
            if (output == null || output.amount <= 0) continue;
            eject.accept(output);
            if (output.amount > 0) accepted = false;
        }
        return accepted;
    }

    static ParallelHelper deferInputs(ParallelHelper helper, List<Runnable> pending,
        ItemStack[] originalItems, FluidStack[] originalFluids) {
        return helper.setRecipeLocked(null, false).setConsumption(false)
            .setInputConsumer((recipe, parallel, fluids, items) -> {
                // Reserve on the helper's copies so the batch pass cannot count the first pass's inputs again.
                recipe.consumeInput(parallel, fluids, items);
                pending.add(() -> recipe.consumeInput(parallel, originalFluids, originalItems));
            });
    }

    static long capacityEU(MTEMultiBlockBase machine) {
        return machine instanceof MTEFusionComputer ? ((MTEFusionComputer) machine).maxEUStore()
            : ((MTELargeFusionComputer) machine).maxEUStore();
    }

    static boolean matches(MTEMultiBlockBase remote, Iterable<MTEMultiBlockBase> machines) {
        for (MTEMultiBlockBase machine : machines) {
            if (machine.getClass() != remote.getClass() || capacityEU(machine) != capacityEU(remote)) return false;
        }
        return true;
    }

    static final class Ignition {
        boolean paid;

        void write(NBTTagCompound tag) { tag.setBoolean("mbHostFusionIgnited", paid); }
        void read(NBTTagCompound tag) { paid = tag.getBoolean("mbHostFusionIgnited"); }

        boolean start(long fee, LongPredicate withdraw, Runnable consume) {
            if (!paid && fee > 0 && !withdraw.test(fee)) return false;
            consume.run();
            paid = true;
            return true;
        }
    }

    static Scope open(MTEMultiBlockBase remote, Ignition ignition, LongPredicate withdraw,
        IntUnaryOperator aggregateParallel) {
        return new Scope(remote, ignition, withdraw, aggregateParallel);
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported fusion field: " + name, error);
        }
    }

    static final class Scope implements AutoCloseable {
        private final MTEMultiBlockBase remote;
        private final ProcessingLogic original;

        Scope(MTEMultiBlockBase remote, Ignition ignition, LongPredicate withdraw,
            IntUnaryOperator aggregateParallel) {
            this.remote = remote;
            try {
                original = (ProcessingLogic) PROCESSING.get(remote);
                Logic replacement = new Logic(remote, capacityEU(remote), ignition, withdraw, aggregateParallel);
                // Advanced Mk IV/V enable perfect OC in createProcessingLogic; retain those native settings.
                replacement.setOverclock(field(ProcessingLogic.class, "overClockTimeReduction").getDouble(original),
                    field(ProcessingLogic.class, "overClockPowerIncrease").getDouble(original));
                replacement.setMaxParallelSupplier(() -> capacity(remote));
                PROCESSING.set(remote, replacement);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot stage fusion processing", error);
            }
        }

        public void close() {
            try {
                PROCESSING.set(remote, original);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot restore fusion processing", error);
            }
        }
    }

    static final class Logic extends ProcessingLogic {
        private final MTEMultiBlockBase remote;
        private final long startupCapacity;
        private final Ignition ignition;
        private final LongPredicate withdraw;
        private final IntUnaryOperator aggregateParallel;
        private final List<Runnable> pendingInputs = new ArrayList<Runnable>();

        Logic(MTEMultiBlockBase remote, long startupCapacity, Ignition ignition, LongPredicate withdraw,
            IntUnaryOperator aggregateParallel) {
            this.remote = remote;
            this.startupCapacity = startupCapacity;
            this.ignition = ignition;
            this.withdraw = withdraw;
            this.aggregateParallel = aggregateParallel;
        }

        @Override
        protected CheckRecipeResult validateRecipe(GTRecipe recipe) {
            long fee = fee(recipe);
            // Capacity is captured before aggregate energy hatches are installed. Paid ignition never bypasses tiers.
            if (fee > startupCapacity) return CheckRecipeResultRegistry.insufficientStartupPower(BigInteger.valueOf(fee));
            if (remote instanceof MTELargeFusionComputer && recipe.mEUt > GTValues.V[((MTELargeFusionComputer) remote).tier()]) {
                return CheckRecipeResultRegistry.insufficientPower(recipe.mEUt);
            }
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        @Override
        protected ParallelHelper createParallelHelper(GTRecipe recipe) {
            pendingInputs.clear();
            int recipeCap = remote instanceof MTELargeFusionComputer
                ? parallelProduct(((MTELargeFusionComputer) remote).getMaxPara(),
                    ((MTELargeFusionComputer) remote).extraPara(fee(recipe))) : capacity(remote);
            // Native large fusion overwrites maxParallel in validation; cap per recipe without losing the host budget.
            return deferInputs(super.createParallelHelper(recipe)
                .setMaxParallel(Math.min(maxParallel, aggregateParallel.applyAsInt(recipeCap))), pendingInputs,
                inputItems, inputFluids);
        }

        @Override
        protected OverclockCalculator createOverclockCalculator(GTRecipe recipe) {
            return ((IOverclockDescriptionProvider) remote).getOverclockDescriber()
                .createCalculator(super.createOverclockCalculator(recipe), recipe)
                .setEUt(availableVoltage).setAmperage(availableAmperage);
        }

        @Override
        protected CheckRecipeResult onRecipeStart(GTRecipe recipe) {
            // Parallel/output/power checks have passed. No real inputs have been consumed yet.
            // The host has no fusion charging buffer: hosted recipes waive ignition, but retain capacity validation.
            if (!ignition.start(0L, withdraw, () -> pendingInputs.forEach(Runnable::run))) {
                return CheckRecipeResultRegistry.insufficientStartupPower(BigInteger.valueOf(fee(recipe)));
            }
            pendingInputs.clear();
            if (remote instanceof MTEFusionComputer) ((MTEFusionComputer) remote).mLastRecipe = recipe;
            if (remote instanceof MTELargeFusionComputer) {
                ((MTELargeFusionComputer) remote).lastRecipe = recipe;
                ((MTELargeFusionComputer) remote).para = getCurrentParallels();
            }
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        private static long fee(GTRecipe recipe) {
            return Math.max(0L, recipe.getMetadataOrDefault(GTRecipeConstants.FUSION_THRESHOLD, 0L));
        }
    }
}

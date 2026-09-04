package com.gtnhblueprints.machine;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.chemplant.MTEChemicalPlant;

/** Scoped recipe logic replacement: no changes to physical plant tiers or catalyst inventories. */
final class HostedChemicalPlantSupport {

    private static final Field PROCESSING = field(MTEMultiBlockBase.class, "processingLogic");
    private static final Field[] TIERS = {
        field(MTEChemicalPlant.class, "mSolidCasingTier"),
        field(MTEChemicalPlant.class, "mMachineCasingTier"),
        field(MTEChemicalPlant.class, "mPipeCasingTier"),
        field(MTEChemicalPlant.class, "mCoilTier") };

    private final Map<MTEMultiBlockBase, HostedChemicalPlantProcessingLogic> cachedLogic =
        new IdentityHashMap<MTEMultiBlockBase, HostedChemicalPlantProcessingLogic>();

    static boolean isChemicalPlant(MTEMultiBlockBase remote) {
        return remote instanceof MTEChemicalPlant;
    }

    static boolean hasMatchingConfiguration(MTEMultiBlockBase representative, Iterable<MTEMultiBlockBase> machines) {
        long[] expected = configuration(representative);
        for (MTEMultiBlockBase machine : machines) {
            if (!isChemicalPlant(machine) || !Arrays.equals(expected, configuration(machine))) return false;
        }
        return true;
    }

    private static long[] configuration(MTEMultiBlockBase remote) {
        long[] result = new long[TIERS.length + 1];
        try {
            for (int i = 0; i < TIERS.length; i++) result[i] = TIERS[i].getInt(remote);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read chemical plant tiers", error);
        }
        result[TIERS.length] = remote.getMaxInputVoltage();
        return result;
    }

    Scope open(MTEMultiBlockBase remote, List<ItemStack> catalysts) {
        HostedChemicalPlantProcessingLogic logic = cachedLogic.get(remote);
        if (logic == null) {
            logic = new HostedChemicalPlantProcessingLogic(MTEChemicalPlant::isCatalyst);
            logic.setMaxParallelSupplier(remote::getTrueParallel);
            cachedLogic.put(remote, logic);
        }
        logic.configure((int) configuration(remote)[0], catalysts);
        return new Scope(remote, logic);
    }

    void release(MTEMultiBlockBase remote) {
        cachedLogic.remove(remote);
    }

    private static Field field(Class<?> declaringClass, String name) {
        try {
            // Resolve only a known field, never enumerate client-only machine method signatures on a server.
            Field field = declaringClass.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported chemical plant field: " + name, error);
        }
    }

    static final class Scope implements AutoCloseable {

        private final MTEMultiBlockBase remote;
        private final ProcessingLogic original;

        Scope(MTEMultiBlockBase remote, ProcessingLogic replacement) {
            this.remote = remote;
            try {
                original = (ProcessingLogic) PROCESSING.get(remote);
                PROCESSING.set(remote, replacement);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot install hosted chemical plant logic", error);
            }
        }

        @Override
        public void close() {
            try {
                PROCESSING.set(remote, original);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot restore original chemical plant logic", error);
            }
        }
    }
}

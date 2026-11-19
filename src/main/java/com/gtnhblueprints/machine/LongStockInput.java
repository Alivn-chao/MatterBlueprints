package com.gtnhblueprints.machine;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.Platform;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/** Short-lived recipe inputs backed by one AE extraction per configured resource. */
final class LongStockInput<T, S extends IAEStack<S>> {
    // Limit the amount of work spent presenting int-sized GT stacks for enormous ME inventories.
    private static final int STACKS_PER_RESOURCE = 64;
    static final long WINDOW = (long) Integer.MAX_VALUE * STACKS_PER_RESOURCE;

    private final Map<T, Slot<T, S>> slots = new IdentityHashMap<>();
    private final ToIntFunction<T> amount;

    private static final class Slot<T, S> {
        final S key;
        final long available;
        List<T> extra;

        Slot(S key, long available) {
            this.key = key;
            this.available = available;
        }
    }

    LongStockInput(ToIntFunction<T> amount) {
        this.amount = amount;
    }

    void remember(T primary, S key, long available) {
        slots.put(primary, new Slot<>(key, Math.min(available, WINDOW)));
    }

    void appendExtra(List<T> inputs, BiFunction<Integer, T, T> copy) {
        int initialSize = inputs.size();
        for (int i = 0; i < initialSize; i++) {
            T primary = inputs.get(i);
            Slot<T, S> slot = slots.get(primary);
            if (slot == null || slot.available <= Integer.MAX_VALUE) continue;
            if (slot.extra == null) {
                slot.extra = new ArrayList<>();
                for (long left = slot.available - Integer.MAX_VALUE; left > 0; left -= Integer.MAX_VALUE) {
                    slot.extra.add(copy.apply((int) Math.min(left, Integer.MAX_VALUE), primary));
                }
            }
            inputs.addAll(slot.extra);
        }
    }

    @FunctionalInterface
    interface Inventory<S extends IAEStack<S>> {
        IMEMonitor<S> get() throws GridAccessException;
    }

    CheckRecipeResult settle(MTEMultiBlockBase controller, AENetworkProxy proxy, Inventory<S> inventory,
        BaseActionSource source, String failureKey) {
        Map<S, Long> used = consumption();
        clear();
        if (used.isEmpty()) return CheckRecipeResultRegistry.SUCCESSFUL;
        try {
            IMEMonitor<S> storage = inventory.get();
            IEnergyGrid energy = proxy.getEnergy();
            for (Map.Entry<S, Long> entry : used.entrySet()) {
                long amount = entry.getValue();
                S request = entry.getKey().copy().setStackSize(amount);
                S result = Platform.poweredExtraction(energy, storage, request, source);
                if (result == null || result.getStackSize() != amount) {
                    controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
                    return SimpleCheckRecipeResult.ofFailurePersistOnShutdown(failureKey);
                }
            }
        } catch (GridAccessException unavailable) {
            controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown(failureKey);
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    static <S extends IAEStack<S>> S available(IMEMonitor<S> inventory, S key, BaseActionSource source) {
        return inventory.extractItems(key.copy().setStackSize(Long.MAX_VALUE), Actionable.SIMULATE, source);
    }

    Map<S, Long> consumption() {
        Map<S, Long> used = new IdentityHashMap<>();
        slots.forEach((primary, slot) -> {
            long visible = Math.min(slot.available, Integer.MAX_VALUE);
            long remaining = amount.applyAsInt(primary);
            if (slot.extra != null) {
                visible = slot.available;
                for (T extra : slot.extra) remaining += amount.applyAsInt(extra);
            }
            if (remaining < visible) used.put(slot.key, visible - remaining);
        });
        return used;
    }

    void clear() {
        slots.clear();
    }
}

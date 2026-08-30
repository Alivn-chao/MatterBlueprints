package com.gtnhblueprints.model;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext;
import com.recursive_pineapple.matter_manipulator.common.building.IPseudoInventory;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;

/** A small, transaction-like adapter for consuming a configured inventory item's fluid from ME. */
final class AeFluidAccess {

    private static final String REQUIREMENT_CONTEXT =
        "com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer$BlockItemCheckContext";

    private final IMEMonitor<IAEFluidStack> monitor;
    private final IAEFluidStack request;
    private final PlayerSource source;

    private AeFluidAccess(IMEMonitor<IAEFluidStack> monitor, IAEFluidStack request, PlayerSource source) {
        this.monitor = monitor;
        this.request = request;
        this.source = source;
    }

    static boolean isRequirementScan(IPseudoInventory inventory) {
        return inventory != null && REQUIREMENT_CONTEXT.equals(inventory.getClass().getName());
    }

    static AeFluidAccess prepare(IPseudoInventory inventory, FluidStack fluid) {
        if (!(inventory instanceof IBlockApplyContext) || fluid == null || fluid.amount <= 0) return null;
        IBlockApplyContext context = (IBlockApplyContext) inventory;
        EntityPlayer player = context.getRealPlayer();
        if (player == null) return null;
        if (player.capabilities.isCreativeMode) return new AeFluidAccess(null, null, null);

        ItemStack manipulator = player.getCurrentEquippedItem();
        if (manipulator == null || !(manipulator.getItem() instanceof ItemMatterManipulator)) return null;
        MMState state = ItemMatterManipulator.getState(manipulator).clone();
        if (!state.connectToMESystem() || !state.hasMEConnection() || !state.canInteractWithAE(player)) return null;

        IMEMonitor<IAEFluidStack> monitor = state.storageGrid.getFluidInventory();
        IAEFluidStack request = AEApi.instance().storage().createFluidStack(fluid.copy());
        if (monitor == null || request == null) return null;
        request.setStackSize(fluid.amount);
        return new AeFluidAccess(monitor, request, new PlayerSource(player, state.actionHost));
    }

    boolean hasEnough() {
        if (monitor == null) return true; // Creative mode.
        IAEFluidStack found = monitor.extractItems(request.copy(), Actionable.SIMULATE, source);
        return found != null && found.getStackSize() >= request.getStackSize();
    }

    boolean extract() {
        if (monitor == null) return true; // Creative mode.
        IAEFluidStack found = monitor.extractItems(request.copy(), Actionable.MODULATE, source);
        if (found != null && found.getStackSize() >= request.getStackSize()) return true;

        // A concurrent ME change can make the real extraction smaller than its
        // simulation. Put the partial result back so neither fluid nor flask is lost.
        if (found != null && found.getStackSize() > 0) {
            monitor.injectItems(found, Actionable.MODULATE, source);
        }
        return false;
    }
}

package com.gtnhblueprints.machine;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/** The standard GT machine selector stays intact; the extra slot is dedicated to the wireless upgrade. */
final class HostedMachineGui extends MTEMultiBlockBaseGui<MTEHostedMachineController> {

    HostedMachineGui(MTEHostedMachineController host) {
        super(host);
    }

    @Override
    protected Flow createRightPanelGapRow(ModularPanel panel, PanelSyncManager syncManager) {
        BooleanSyncValue wireless = new BooleanSyncValue(
            multiblock::isWirelessMode,
            enabled -> multiblock.setWirelessMode(enabled, syncManager.getPlayer())).allowC2S();
        ModularSlot upgrade = new ModularSlot(multiblock.inventoryHandler, MTEHostedMachineController.WIRELESS_UPGRADE_SLOT) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return multiblock.isItemValidForSlot(MTEHostedMachineController.WIRELESS_UPGRADE_SLOT, stack);
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }

            @Override
            public boolean canTakeStack(EntityPlayer player) {
                return multiblock.canRemoveWirelessUpgrade()
                    && (player.worldObj.isRemote || multiblock.canConfigureWireless(player));
            }

            @Override
            public void onSlotChanged() {
                super.onSlotChanged();
                multiblock.mStructureChanged = true;
                multiblock.markDirty();
            }
        }.singletonSlotGroup();
        return super.createRightPanelGapRow(panel, syncManager)
            .child(new ItemSlot().slot(upgrade)
                .tooltipBuilder(t -> t.addLine(IKey.lang("matterblueprints.wireless.upgrade_slot"))))
            .child(new ToggleButton().value(wireless).size(36, 18)
                .overlay(IKey.lang("matterblueprints.wireless.button"))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.lang("matterblueprints.wireless.upgrade_desc"));
                    t.addLine(IKey.lang("matterblueprints.wireless.owner_only"));
                    t.addLine(IKey.dynamic(() -> multiblock.isWirelessMode()
                        ? net.minecraft.util.StatCollector.translateToLocal("matterblueprints.wireless.on")
                        : net.minecraft.util.StatCollector.translateToLocal("matterblueprints.wireless.off")));
                }));
    }
}

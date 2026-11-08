package com.gtnhblueprints.machine;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import gregtech.api.modularui2.GTGuis;
import gregtech.api.modularui2.GTGuiTextures;

/** Uses the native bus's automatic list and physical manual slot, without an extra inventory. */
final class MEMarkingControls {
    private MEMarkingControls() {}
    static Flow create(MTEQuantizedMEInputBus port, ModularPanel parent, PanelSyncManager sync) {
        IPanelHandler settings = sync.syncedPanel("markedAutoSettings", true, (manager, handler) ->
            GTGuis.createPopUpPanel("markedAutoSettings").size(170, 112).relative(parent).left(3).top(20)
                .child(Flow.col().coverChildren().left(8).top(18).childPadding(5)
                    .child(IKey.lang("GT5U.machines.stocking_bus.min_stack_size").asWidget())
                    .child(new TextFieldWidget().size(150, 18).numbersInt(1, Integer.MAX_VALUE)
                        .value(new IntSyncValue(port::getMinAutoPullStackSize, port::setMinAutoPullStackSize).allowC2S()))
                    .child(IKey.lang("GT5U.machines.stocking_bus.refresh_time").asWidget())
                    .child(new TextFieldWidget().size(150, 18).numbersInt(1, Integer.MAX_VALUE)
                        .value(new IntSyncValue(port::getAutoPullRefreshTime, port::setAutoPullRefreshTime).allowC2S()))));
        IPanelHandler manual = sync.syncedPanel("markedManualItems", true, (manager, handler) ->
            GTGuis.createPopUpPanel("markedManualItems").size(120, 66).relative(parent).left(3).top(20)
                .child(IKey.lang("matterblueprints.stock.manual").asWidget().left(6).top(18))
                .child(new ItemSlot().left(50).top(34).slot(
                    new ModularSlot(port.inventoryHandler, port.getManualSlot()).singletonSlotGroup()
                        .changeListener((stack, amountOnly, client, init) -> {
                            if (!client && port.getBaseMetaTileEntity() != null) {
                                port.getBaseMetaTileEntity().enableTicking();
                                port.onContentsChanged(port.getManualSlot());
                            }
                        }))));
        return Flow.row().coverChildren().childPadding(2)
            .child(new ToggleButton() {
                @Override public Result onMousePressed(int button) {
                    if (button == 0) { next(); playClickSound(); return Result.SUCCESS; }
                    if (button == 1) {
                        if (settings.isPanelOpen()) settings.closePanel(); else settings.openPanel();
                        playClickSound(); return Result.SUCCESS;
                    }
                    return Result.IGNORE;
                }
            }.size(18).value(new BooleanSyncValue(port::isAutoPullItemList, port::setAutoPullItemList).allowC2S())
                .overlay(true, GTGuiTextures.OVERLAY_BUTTON_AUTOPULL_ME)
                .overlay(false, GTGuiTextures.OVERLAY_BUTTON_AUTOPULL_ME_DISABLED)
                .addTooltipLine(IKey.lang("GT5U.machines.stocking_bus.auto_pull.tooltip.1"))
                .addTooltipLine(IKey.lang("GT5U.machines.stocking_bus.auto_pull.tooltip.2")))
            .child(new ButtonWidget<>().size(18).overlay(IKey.str("+"))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    if (manual.isPanelOpen()) manual.closePanel(); else manual.openPanel();
                    return true;
                }).addTooltipLine(IKey.lang("matterblueprints.stock.manual")));
    }
}

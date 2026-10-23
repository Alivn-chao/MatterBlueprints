package com.gtnhblueprints.machine;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import gregtech.common.tileentities.machines.MTEHatchInputME.Slot;
import gregtech.common.gui.modularui.adapter.MTEHatchInputMESlotAdapter;
import gregtech.api.util.GTDataUtils;
import gregtech.api.util.GTUtility;

/** Compact marks; quantities live in a middle-click popup instead of covering the inventory. */
final class MEMarkingGui {
    static final int COLUMNS = 8;
    static final int ROWS = 2;
    private MEMarkingGui() {}

    private static IPanelHandler amountEditor(String key, ModularPanel parent, PanelSyncManager sync,
        IntSupplier getter, IntConsumer setter) {
        return sync.syncedPanel(key, true, (manager, handler) -> gregtech.api.modularui2.GTGuis.createPopUpPanel(key).size(166, 82)
            .relative(parent).left(5).top(28)
            .child(Flow.col().coverChildren().left(8).top(18).childPadding(6)
                .child(IKey.lang("matterblueprints.stock.edit_amount").asWidget())
                .child(new TextFieldWidget().size(148, 18)
                    .value(new IntSyncValue(getter, setter).allowC2S())
                    .numbersInt(0, Integer.MAX_VALUE).formatAsInteger(true).setMaxLength(10))
                .child(IKey.lang("matterblueprints.stock.zero").asWidget())));
    }

    static Flow items(MTEQuantizedMEInputBus port, ModularPanel panel, PanelSyncManager sync) {
        Flow content = Flow.col().coverChildren().childPadding(2)
            .child(IKey.lang("matterblueprints.stock.items").asWidget());
        ItemStackHandler marks = new ItemStackHandler(16) {
            @Override public ItemStack getStackInSlot(int index) {
                gregtech.common.tileentities.machines.MTEHatchInputBusME.Slot slot = port.viewSlots()[index];
                return slot == null ? null : slot.config;
            }
            @Override public void setStackInSlot(int index, ItemStack stack) {
                if (stack != null) for (int i = 0; i < 16; i++) {
                    if (i != index && port.viewSlots()[i] != null && GTUtility.areStacksEqual(port.viewSlots()[i].config, stack)) return;
                }
                port.markItem(index, stack);
            }
        };
        for (int row = 0; row < ROWS; row++) {
            Flow line = Flow.row().coverChildren();
            for (int col = 0; col < COLUMNS; col++) {
                final int index = row * COLUMNS + col;
                IntSyncValue amount = new IntSyncValue(() -> port.getMarkedAmount(index));
                sync.syncValue("itemAmount" + index, amount);
                IPanelHandler editor = amountEditor("itemAmountEditor" + index, panel, sync,
                    () -> port.getMarkedAmount(index), value -> port.setMarkedAmount(index, value));
                line.child(new PhantomItemSlot() {
                    @Override public Result onMousePressed(int button) {
                        if (button == 2) { editor.openPanel(); return Result.SUCCESS; }
                        return super.onMousePressed(button);
                    }
                    @Override public boolean onMouseScroll(UpOrDown direction, int count) { return false; }
                }.slot(new ModularSlot(marks, index).singletonSlotGroup())
                    .tooltipAutoUpdate(true).tooltipDynamic(tooltip -> {
                        tooltip.addLine(IKey.lang("matterblueprints.stock.middle_click"));
                        tooltip.addLine(IKey.lang("matterblueprints.stock.limit", amount.getIntValue()));
                        gregtech.common.tileentities.machines.MTEHatchInputBusME.Slot slot = port.viewSlots()[index];
                        tooltip.addLine(IKey.lang("matterblueprints.stock.available", slot == null ? 0 : slot.extractedAmount));
                    }));
            }
            content.child(line);
        }
        return content;
    }

    static void syncFluids(MTEQuantizedMEInputHatch port, PanelSyncManager sync) {
        sync.syncValue("fluidSlots", GenericListSyncHandler.<Slot>builder()
            .getter(() -> GTDataUtils.mapToList(port.viewSlots(), slot -> slot == null ? null : slot.copy()))
            .setter(values -> System.arraycopy(values.toArray(new Slot[0]), 0, port.viewSlots(), 0, 16))
            .adapter(new MTEHatchInputMESlotAdapter()).build());
    }

    static Flow fluids(MTEQuantizedMEInputHatch port, ModularPanel panel, PanelSyncManager sync) {
        Flow content = Flow.col().coverChildren().childPadding(2)
            .child(IKey.lang("matterblueprints.stock.fluids").asWidget());
        for (int row = 0; row < ROWS; row++) {
            Flow line = Flow.row().coverChildren();
            for (int col = 0; col < COLUMNS; col++) {
                final int index = row * COLUMNS + col;
                IntSyncValue amount = new IntSyncValue(() -> port.getMarkedAmount(index));
                sync.syncValue("fluidAmount" + index, amount);
                IPanelHandler editor = amountEditor("fluidAmountEditor" + index, panel, sync,
                    () -> port.getMarkedAmount(index), value -> port.setMarkedAmount(index, value));
                line.child(new FluidSlot() {
                    @Override public Result onMousePressed(int button) {
                        if (button == 2) { editor.openPanel(); return Result.SUCCESS; }
                        return super.onMousePressed(button);
                    }
                    @Override public boolean onMouseScroll(UpOrDown direction, int count) { return false; }
                }.syncHandler(new FluidSlotSyncHandler(tank(port, index)).phantom(true).controlsAmount(false))
                    .tooltipAutoUpdate(true).tooltipDynamic(tooltip -> {
                        tooltip.addLine(IKey.lang("matterblueprints.stock.middle_click"));
                        tooltip.addLine(IKey.lang("matterblueprints.stock.limit", amount.getIntValue()));
                        Slot slot = port.viewSlots()[index];
                        tooltip.addLine(IKey.lang("matterblueprints.stock.available", slot == null ? 0 : slot.extractedAmount));
                    }));
            }
            content.child(line);
        }
        return content;
    }

    private static IFluidTank tank(MTEQuantizedMEInputHatch port, int index) {
        return new IFluidTank() {
            @Override public FluidStack getFluid() { Slot slot = port.viewSlots()[index]; return slot == null ? null : slot.config; }
            @Override public int getFluidAmount() { return getFluid() == null ? 0 : 1; }
            @Override public int getCapacity() { return 1; }
            @Override public FluidTankInfo getInfo() { return new FluidTankInfo(this); }
            @Override public int fill(FluidStack fluid, boolean commit) {
                if (fluid == null) return 0;
                for (int i = 0; i < 16; i++) if (i != index && port.viewSlots()[i] != null
                    && port.viewSlots()[i].config.isFluidEqual(fluid)) return 0;
                if (commit) port.markFluid(index, fluid);
                return 1;
            }
            @Override public FluidStack drain(int amount, boolean commit) {
                FluidStack previous = getFluid();
                if (commit) port.markFluid(index, null);
                return previous == null ? null : previous.copy();
            }
        };
    }
}

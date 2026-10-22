package com.gtnhblueprints.machine;

import java.util.function.IntUnaryOperator;
import java.util.function.BiConsumer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import gregtech.common.tileentities.machines.MTEHatchInputME.Slot;
import gregtech.common.gui.modularui.adapter.MTEHatchInputMESlotAdapter;
import gregtech.api.util.GTDataUtils;

final class MEMarkingGui {
    private MEMarkingGui() {}
    static Flow amounts(IntUnaryOperator getter, BiConsumer<Integer, Integer> setter) {
        Flow content = Flow.col().coverChildren().child(IKey.lang("matterblueprints.stock.amounts").asWidget());
        for (int row = 0; row < 4; row++) {
            Flow line = Flow.row().coverChildren().childPadding(2);
            for (int col = 0; col < 4; col++) {
                final int i = row * 4 + col;
                line.child(new TextFieldWidget().value(new IntSyncValue(() -> getter.applyAsInt(i), value -> setter.accept(i, value)).allowC2S())
                    .numbersInt(0, Integer.MAX_VALUE).formatAsInteger(true).setMaxLength(10).width(38)
                    .addTooltipLine(IKey.lang("matterblueprints.stock.slot", i + 1)));
            }
            content.child(line);
        }
        return content;
    }
    static void syncFluids(MTEMEInputAssembly.FluidPort port, PanelSyncManager sync) {
        sync.syncValue("fluidSlots", GenericListSyncHandler.<Slot>builder()
            .getter(() -> GTDataUtils.mapToList(port.viewSlots(), slot -> slot == null ? null : slot.copy()))
            .setter(values -> System.arraycopy(values.toArray(new Slot[0]), 0, port.viewSlots(), 0, 16))
            .adapter(new MTEHatchInputMESlotAdapter()).build());
    }
    static Flow fluids(MTEMEInputAssembly.FluidPort port) {
        Flow content = Flow.col().coverChildren().child(IKey.lang("matterblueprints.stock.fluids").asWidget());
        for (int row = 0; row < 4; row++) {
            Flow line = Flow.row().coverChildren().childPadding(4);
            for (int col = 0; col < 4; col++) {
                final int i = row * 4 + col;
                Flow cell = Flow.row().coverChildren();
                cell.child(new FluidSlot().syncHandler(new FluidSlotSyncHandler(tank(port, i, true)).phantom(true).controlsAmount(false)));
                cell.child(new TextFieldWidget().value(new IntSyncValue(() -> port.getMarkedAmount(i), amount -> port.setMarkedAmount(i, amount)).allowC2S())
                    .numbersInt(0, Integer.MAX_VALUE).formatAsInteger(true).setMaxLength(10).width(55));
                cell.child(new FluidSlot().syncHandler(new FluidSlotSyncHandler(tank(port, i, false)).canFillSlot(false).canDrainSlot(false)));
                line.child(cell);
            }
            content.child(line);
        }
        return content;
    }
    private static IFluidTank tank(MTEMEInputAssembly.FluidPort port, int index, boolean config) {
        return new IFluidTank() {
            @Override public FluidStack getFluid() {
                Slot slot = port.viewSlots()[index];
                return slot == null ? null : config ? slot.config : slot.getOriginalExtracted();
            }
            @Override public int getFluidAmount() { FluidStack fluid = getFluid(); return fluid == null ? 0 : fluid.amount; }
            @Override public int getCapacity() { return config ? 1 : Integer.MAX_VALUE; }
            @Override public FluidTankInfo getInfo() { return new FluidTankInfo(getFluid(), getCapacity()); }
            @Override public int fill(FluidStack fluid, boolean commit) {
                if (config && commit) port.markFluid(index, fluid);
                return fluid == null ? 0 : fluid.amount;
            }
            @Override public FluidStack drain(int amount, boolean commit) {
                FluidStack previous = getFluid();
                if (config && commit) port.markFluid(index, null);
                return previous == null ? null : previous.copy();
            }
        };
    }
}

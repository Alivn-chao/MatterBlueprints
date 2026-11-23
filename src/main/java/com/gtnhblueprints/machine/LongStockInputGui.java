package com.gtnhblueprints.machine;

import java.util.Locale;
import java.util.function.LongSupplier;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.modularui2.widget.builder.ItemSlotGridBuilder;

/** Uses GT's stock icons with a separate 64-bit quantity for rendering and tooltips. */
public final class LongStockInputGui {
    private static final String STOCK_GROUP = "stock_inv";
    private static final int GRID_WIDTH = 4;
    private static final int SLOT_COUNT = GRID_WIDTH * GRID_WIDTH;

    private LongStockInputGui() {}

    public static Grid items(PanelSyncManager sync, IItemHandlerModifiable slots, MTELongStockInputBus bus) {
        LongSyncValue[] amounts = new LongSyncValue[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            final int index = i;
            amounts[i] = syncAmount(sync, index, bus.getBaseMetaTileEntity().getWorld()::getTotalWorldTime,
                () -> bus.getDisplayAmount(index));
        }
        return new ItemSlotGridBuilder(slots, sync).size(GRID_WIDTH, GRID_WIDTH)
            .slotGroupKey(STOCK_GROUP)
            .indexOffset(SLOT_COUNT)
            .accessibility(false, false)
            .itemSlotSupplier(() -> new ItemSlot() {
                private long displayed() {
                    return amounts[getSlot().getSlotIndex() - SLOT_COUNT].getLongValue();
                }

                @Override
                @SideOnly(Side.CLIENT)
                protected void drawSlotAmountText(int amount, String prefix) {
                    GuiDraw.drawStandardSlotAmountText(displayed(), prefix, getArea());
                }

                @Override
                public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
                    super.buildTooltip(stack, tooltip);
                    if (stack != null) addAmount(tooltip, displayed(), false);
                }
            }.backgroundOverlay(GTGuiTextures.SLOT_ITEM_DARK))
            .build();
    }

    public static Grid fluids(PanelSyncManager sync, MTELongStockInputHatch hatch) {
        sync.registerSlotGroup(STOCK_GROUP, GRID_WIDTH);
        return new Grid().coverChildren().gridOfWidthHeight(GRID_WIDTH, GRID_WIDTH, (x, y, index) -> {
            LongSyncValue amount = syncAmount(sync, index, hatch.getBaseMetaTileEntity().getWorld()::getTotalWorldTime,
                () -> hatch.getDisplayAmount(index));
            IFluidTank icon = new IFluidTank() {
                @Override
                public FluidStack getFluid() { return hatch.getDisplayFluid(index); }

                @Override
                public int getFluidAmount() {
                    FluidStack stack = getFluid();
                    return stack == null ? 0 : stack.amount;
                }

                @Override
                public int getCapacity() { return Integer.MAX_VALUE; }

                @Override
                public FluidTankInfo getInfo() { return new FluidTankInfo(this); }

                @Override
                public int fill(FluidStack resource, boolean doFill) { return 0; }

                @Override
                public FluidStack drain(int amount, boolean doDrain) { return null; }
            };
            return new FluidSlot() {
                @Override
                protected boolean displayAmountText() { return false; }

                @Override
                @SideOnly(Side.CLIENT)
                public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> theme) {
                    super.drawOverlay(context, theme);
                    if (getFluidStack() != null) {
                        GuiDraw.drawStandardSlotAmountText(amount.getLongValue(), null, getArea());
                    }
                }

                @Override
                protected void addToolTip(RichTooltip tooltip) {
                    FluidStack fluid = getFluidStack();
                    if (fluid == null) {
                        tooltip.addLine(IKey.lang("modularui2.fluid.empty"));
                        return;
                    }
                    tooltip.addFromFluid(fluid);
                    addAmount(tooltip, amount.getLongValue(), true);
                    addAdditionalFluidInfo(tooltip, fluid);
                }
            }.syncHandler(new FluidSlotSyncHandler(icon) {
                @Override
                protected void tryClickPhantom(MouseData mouse, ItemStack cursor) {}

                @Override
                public void tryScrollPhantom(MouseData mouse) {}
            }.phantom(true).controlsAmount(false))
                .backgroundOverlay(GTGuiTextures.SLOT_ITEM_DARK);
        });
    }

    private static void addAmount(RichTooltip tooltip, long amount, boolean fluid) {
        tooltip.addLine(IKey.lang(fluid ? "matterblueprints.stock.me_available_fluid"
            : "matterblueprints.stock.me_available_item", String.format(Locale.ROOT, "%,d", amount)));
    }

    private static LongSyncValue syncAmount(PanelSyncManager sync, int index, LongSupplier tick, LongSupplier read) {
        LongSyncValue value = new LongSyncValue(new LongSupplier() {
            private long nextRefresh = Long.MIN_VALUE;
            private long amount;

            @Override
            public long getAsLong() {
                long now = tick.getAsLong();
                if (now >= nextRefresh) {
                    amount = read.getAsLong();
                    nextRefresh = now + 20L;
                }
                return amount;
            }
        });
        sync.syncValue("mbLongStock" + index, value);
        return value;
    }
}

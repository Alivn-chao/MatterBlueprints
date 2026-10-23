package com.gtnhblueprints.machine;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;

final class MEOutputAssemblyGui extends MTEHatchBaseGui<MTEMEOutputAssembly> {
    MEOutputAssemblyGui(MTEMEOutputAssembly machine) { super(machine); }
    @Override protected int getBasePanelHeight() { return 238; }
    @Override protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager sync) {
        return Flow.col().coverChildren().childPadding(5)
            .child(section("items", machine.getProvider(), new ModularSlot(machine.inventoryHandler, 0).singletonSlotGroup(), sync))
            .child(section("fluids", machine.fluids.provider, new ModularSlot(machine.fluids.cell, 0).singletonSlotGroup(), sync));
    }
    private Flow section(String key, MTEHatchOutputMEBase<?> provider, ModularSlot drive, PanelSyncManager sync) {
        BooleanSyncValue caching = new BooleanSyncValue(provider::getCacheMode, provider::setCacheMode).allowC2S();
        BooleanSyncValue checking = new BooleanSyncValue(provider::getCheckMode, provider::setCheckMode).allowC2S();
        LongSyncValue capacity = new LongSyncValue(provider::getCacheCapacity);
        LongSyncValue stored = new LongSyncValue(provider::getCachedAmount);
        sync.syncValue(key + "Capacity", capacity);
        sync.syncValue(key + "Stored", stored);
        return Flow.col().coverChildren().childPadding(2)
            .child(IKey.lang("matterblueprints.output.drive." + key).asWidget())
            .child(Flow.row().coverChildren().collapseDisabledChild()
                .child(new ItemSlot().slot(drive))
                .child(new ToggleButton().value(caching).overlay(GuiTextures.FOLDER)
                    .addTooltipLine(IKey.lang("GT5U.hatch.outputme.toggle_caching")))
                .child(new TextFieldWidget().size(75, 14).marginLeft(5).formatAsInteger(true)
                    .value(new IntSyncValue(provider::getPriority, provider::setPriority).allowC2S())
                    .numbersInt(Integer.MIN_VALUE, Integer.MAX_VALUE).setMaxLength(10)
                    .setEnabledIf(widget -> caching.getBoolValue()))
                .child(new ToggleButton().value(checking).overlay(GuiTextures.SEARCH)
                    .addTooltipLine(IKey.lang("GT5U.hatch.outputme.toggle_checking"))))
            .child(IKey.dynamic(() -> net.minecraft.util.StatCollector.translateToLocalFormatted(
                "matterblueprints.output.capacity", compact(stored.getLongValue()), compact(capacity.getLongValue()))).asWidget());
    }
    private static String compact(long amount) {
        if (amount < 1_000_000L) return Long.toString(amount);
        String[] units = { "M", "G", "T", "P", "E" };
        double scaled = amount / 1_000_000D;
        int index = 0;
        while (scaled >= 1000 && index < units.length - 1) { scaled /= 1000; index++; }
        return String.format(java.util.Locale.ROOT, "%.2f%s", scaled, units[index]);
    }
}

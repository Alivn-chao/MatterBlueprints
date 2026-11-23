package com.gtnhblueprints.mixin;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.gtnhblueprints.machine.LongStockInputGui;
import com.gtnhblueprints.machine.MTELongStockInputBus;

import gregtech.common.gui.modularui.hatch.MTEHatchInputBusMEGui;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEHatchInputBusMEGui.class, remap = false)
public abstract class MixinLongStockItemGui extends MTEHatchBaseGui<MTEHatchInputBusME> {
    protected MixinLongStockItemGui(MTEHatchInputBusME machine) {
        super(machine);
    }

    @Inject(method = "createStockSlots", at = @At("HEAD"), cancellable = true, require = 1)
    private void matterblueprints$showLongAmounts(PanelSyncManager sync, IItemHandlerModifiable slots,
        CallbackInfoReturnable<Grid> result) {
        if (machine instanceof MTELongStockInputBus) {
            result.setReturnValue(LongStockInputGui.items(sync, slots, (MTELongStockInputBus) machine));
        }
    }
}

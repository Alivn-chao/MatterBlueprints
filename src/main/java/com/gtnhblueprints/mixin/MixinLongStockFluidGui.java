package com.gtnhblueprints.mixin;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.gtnhblueprints.machine.LongStockInputGui;
import com.gtnhblueprints.machine.MTELongStockInputHatch;

import gregtech.common.gui.modularui.hatch.MTEHatchInputMEGui;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEHatchInputMEGui.class, remap = false)
public abstract class MixinLongStockFluidGui extends MTEHatchBaseGui<MTEHatchInputME> {
    protected MixinLongStockFluidGui(MTEHatchInputME machine) {
        super(machine);
    }

    @Inject(method = "createStockSlots", at = @At("HEAD"), cancellable = true, require = 1)
    private void matterblueprints$showLongAmounts(PanelSyncManager sync, CallbackInfoReturnable<Grid> result) {
        if (machine instanceof MTELongStockInputHatch) {
            result.setReturnValue(LongStockInputGui.fluids(sync, (MTELongStockInputHatch) machine));
        }
    }
}

package com.gtnhblueprints.mixin;

import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import com.gtnhblueprints.machine.MTEMEInputAssembly;
import com.gtnhblueprints.machine.MTEMEOutputAssembly;

/** Let a manual assembly satisfy the standard fluid-port structure requirement as well as the item one. */
@Mixin(targets = "gregtech.api.enums.HatchElement$5", remap = false)
public abstract class MixinHatchElementOutput {
    @Inject(method = "count(Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;)J", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$countAssembly(MTEMultiBlockBase machine, CallbackInfoReturnable<Long> result) {
        result.setReturnValue(result.getReturnValue() + machine.mOutputBusses.stream().filter(hatch -> hatch instanceof MTEMEOutputAssembly).count());
    }
}

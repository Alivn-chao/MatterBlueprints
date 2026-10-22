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
@Mixin(targets = "gregtech.api.enums.HatchElement$3", remap = false)
public abstract class MixinHatchElementInput {
    @Inject(method = "count(Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;)J", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$countAssembly(MTEMultiBlockBase machine, CallbackInfoReturnable<Long> result) {
        result.setReturnValue(result.getReturnValue() + machine.mInputBusses.stream().filter(hatch -> hatch instanceof MTEMEInputAssembly).count());
    }
    @Inject(method = "mteClasses", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$recognizeAssembly(CallbackInfoReturnable<List<? extends Class<? extends IMetaTileEntity>>> result) {
        List<Class<? extends IMetaTileEntity>> classes = new ArrayList<>(result.getReturnValue());
        classes.add(MTEMEInputAssembly.class);
        result.setReturnValue(classes);
    }
}

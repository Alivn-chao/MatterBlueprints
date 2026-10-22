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

@Mixin(value = gregtech.api.enums.HatchElement.class, remap = false)
public abstract class MixinHatchElementClasses {
    @Inject(method = "mteClasses", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$recognizeOutputAssembly(CallbackInfoReturnable<List<? extends Class<? extends IMetaTileEntity>>> result) {
        if ((Object) this != gregtech.api.enums.HatchElement.OutputHatch) return;
        List<Class<? extends IMetaTileEntity>> classes = new ArrayList<>(result.getReturnValue());
        classes.add(MTEMEOutputAssembly.class);
        result.setReturnValue(classes);
    }
}

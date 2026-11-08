package com.gtnhblueprints.mixin;

import java.util.Objects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnhblueprints.machine.HostedBindingSupport;
import com.recursive_pineapple.matter_manipulator.common.building.GTAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@Mixin(value = GTAnalysisResult.class, remap = false)
public abstract class MixinMMHostedBinding implements HostedBindingSupport.Trait {
    // Non-transient: MM and Blueprint JSON must retain this field. No world/live tile references.
    @Unique private String matterblueprints$hostId;

    @Override public String matterblueprints$getHost() { return matterblueprints$hostId; }
    @Override public void matterblueprints$setHost(String id) { matterblueprints$hostId = id; }

    @Inject(method = "<init>(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;)V", at = @At("RETURN"), require = 1)
    private void matterblueprints$capture(IGregTechTileEntity tile, CallbackInfo ci) {
        matterblueprints$hostId = HostedBindingSupport.capture(tile);
    }

    @Inject(method = "apply", at = @At("RETURN"), require = 1)
    private void matterblueprints$apply(IBlockApplyContext context, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) HostedBindingSupport.apply(matterblueprints$hostId, context);
    }

    @Inject(method = "clone()Lcom/recursive_pineapple/matter_manipulator/common/building/GTAnalysisResult;", at = @At("RETURN"), require = 1)
    private void matterblueprints$copy(CallbackInfoReturnable<GTAnalysisResult> cir) {
        ((HostedBindingSupport.Trait) cir.getReturnValue()).matterblueprints$setHost(matterblueprints$hostId);
    }

    @Inject(method = "equals", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$equals(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && other instanceof HostedBindingSupport.Trait) {
            cir.setReturnValue(Objects.equals(matterblueprints$hostId, ((HostedBindingSupport.Trait) other).matterblueprints$getHost()));
        }
    }

    @Inject(method = "hashCode", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$hash(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(31 * cir.getReturnValue() + Objects.hashCode(matterblueprints$hostId));
    }
}

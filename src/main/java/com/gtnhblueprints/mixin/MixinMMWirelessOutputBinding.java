package com.gtnhblueprints.mixin;

import java.util.Objects;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnhblueprints.wireless.WirelessOutputCopy;
import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext;
import com.recursive_pineapple.matter_manipulator.common.building.GTAnalysisResult;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@Mixin(value = GTAnalysisResult.class, remap = false)
public abstract class MixinMMWirelessOutputBinding implements WirelessOutputCopy.Trait {

    // Persist only the channel ID in MM and GTBP JSON. Authorize the player at paste time.
    @Unique private String matterblueprints$wirelessOutputChannel;

    @Override
    public String matterblueprints$getWirelessOutputChannel() { return matterblueprints$wirelessOutputChannel; }

    @Override
    public void matterblueprints$setWirelessOutputChannel(String channel) {
        matterblueprints$wirelessOutputChannel = channel;
    }

    @Inject(method = "<init>(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;)V", at = @At("RETURN"), require = 1)
    private void matterblueprints$captureWirelessOutput(IGregTechTileEntity tile, CallbackInfo ci) {
        matterblueprints$wirelessOutputChannel = WirelessOutputCopy.capture(tile);
    }

    @Inject(method = "apply", at = @At("RETURN"), require = 1)
    private void matterblueprints$applyWirelessOutput(IBlockApplyContext context, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) WirelessOutputCopy.apply(matterblueprints$wirelessOutputChannel, context);
    }

    @Inject(method = "clone()Lcom/recursive_pineapple/matter_manipulator/common/building/GTAnalysisResult;", at = @At("RETURN"), require = 1)
    private void matterblueprints$cloneWirelessOutput(CallbackInfoReturnable<GTAnalysisResult> cir) {
        ((WirelessOutputCopy.Trait) cir.getReturnValue())
            .matterblueprints$setWirelessOutputChannel(matterblueprints$wirelessOutputChannel);
    }

    @Inject(method = "equals", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$equalWirelessOutput(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && other instanceof WirelessOutputCopy.Trait
            && !Objects.equals(matterblueprints$wirelessOutputChannel,
                ((WirelessOutputCopy.Trait) other).matterblueprints$getWirelessOutputChannel())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hashCode", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$hashWirelessOutput(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(31 * cir.getReturnValue() + Objects.hashCode(matterblueprints$wirelessOutputChannel));
    }
}

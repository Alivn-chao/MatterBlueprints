package com.gtnhblueprints.mixin;

import appeng.api.networking.IGrid;
import appeng.me.pathfinding.PathingCalculation;
import com.gtnhblueprints.wireless.WirelessMEChannels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = PathingCalculation.class, remap = false)
public abstract class MixinAEWirelessPathCalculation {
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 32), require = 1, allow = 1)
    private int matterblueprints$controllerFaceCapacity(int capacity, IGrid grid) {
        return WirelessMEChannels.capacity(capacity, WirelessMEChannels.unlimited(grid));
    }
}

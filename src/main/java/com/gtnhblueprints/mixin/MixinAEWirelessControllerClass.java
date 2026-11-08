package com.gtnhblueprints.mixin;

import appeng.api.networking.IGridHost;
import appeng.me.GridNode;
import appeng.me.Grid;
import com.gtnhblueprints.wireless.WirelessMEChannels;
import appeng.tile.networking.TileController;
import com.gtnhblueprints.wireless.TileWirelessMEController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE's path finder indexes controller nodes by exact class, while structure checks use instanceof. */
@Mixin(value = GridNode.class, remap = false)
public abstract class MixinAEWirelessControllerClass {
    @Shadow private Grid myGrid;
    @Shadow public abstract IGridHost getMachine();
    @Inject(method = "getMachineClass", at = @At("HEAD"), cancellable = true, require = 1)
    private void matterblueprints$controllerClass(CallbackInfoReturnable<Class<? extends IGridHost>> result) {
        if (getMachine() instanceof TileWirelessMEController) result.setReturnValue(TileController.class);
    }

    @Inject(method = "getMaxChannels", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$channelCapacity(CallbackInfoReturnable<Integer> result) {
        if (myGrid == null || result.getReturnValue() == 0) return;
        result.setReturnValue(WirelessMEChannels.capacity(result.getReturnValue(),
            WirelessMEChannels.unlimited(myGrid)));
    }
}

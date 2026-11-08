package com.gtnhblueprints.mixin;

import java.util.Set;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.me.cache.PathGridCache;
import appeng.tile.networking.TileController;
import com.gtnhblueprints.wireless.TileWirelessMEController;
import com.gtnhblueprints.wireless.WirelessMEChannels;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PathGridCache.class, remap = false)
public abstract class MixinAEWirelessPathGrid implements WirelessMEChannels.GridState {
    @Shadow @Final private Set<TileController> controllers;
    @Unique private boolean matterblueprints$wirelessNetwork;

    @Inject(method = { "addNode", "removeNode" }, at = @At("RETURN"), require = 2)
    private void matterblueprints$updateCapacity(IGridNode node, IGridHost machine, CallbackInfo ci) {
        if (!(machine instanceof TileWirelessMEController)) return;
        matterblueprints$wirelessNetwork = false;
        for (TileController controller : controllers) {
            if (controller instanceof TileWirelessMEController) {
                matterblueprints$wirelessNetwork = true;
                break;
            }
        }
    }

    @Override
    public boolean matterblueprints$unlimitedChannels() { return matterblueprints$wirelessNetwork; }
}

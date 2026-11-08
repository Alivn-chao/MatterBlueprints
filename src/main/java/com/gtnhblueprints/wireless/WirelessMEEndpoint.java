package com.gtnhblueprints.wireless;

import net.minecraft.tileentity.TileEntity;
import appeng.me.helpers.AENetworkProxy;

public interface WirelessMEEndpoint {
    WirelessMEBinding wireless();
    TileEntity wirelessTile();
    AENetworkProxy getProxy();
    default boolean wirelessSource() { return false; }
}

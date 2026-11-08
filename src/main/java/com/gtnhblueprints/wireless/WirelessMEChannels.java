package com.gtnhblueprints.wireless;

import appeng.api.networking.IGrid;
import appeng.api.networking.pathing.IPathingGrid;

/** Queried during AE path calculation. Updated only when controller membership changes. */
public final class WirelessMEChannels {
    private WirelessMEChannels() {}

    public interface GridState {
        boolean matterblueprints$unlimitedChannels();
    }

    public static boolean unlimited(IGrid grid) {
        if (grid == null) return false;
        IPathingGrid path = grid.getCache(IPathingGrid.class);
        return path instanceof GridState && ((GridState) path).matterblueprints$unlimitedChannels();
    }

    public static int capacity(int nativeCapacity, boolean wirelessNetwork) {
        return wirelessNetwork && nativeCapacity > 0 ? Integer.MAX_VALUE : nativeCapacity;
    }
}

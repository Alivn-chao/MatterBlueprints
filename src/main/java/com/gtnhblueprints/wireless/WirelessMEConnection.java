package com.gtnhblueprints.wireless;

import appeng.api.AEApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;

/** Idempotent lifetime of one terminal-to-controller edge. No relay with a shared channel limit. */
final class WirelessMEConnection {
    interface Factory { IGridConnection connect(IGridNode local, IGridNode source) throws FailedConnection; }
    private final Factory factory;
    private IGridNode local;
    private IGridNode source;
    private IGridConnection connection;
    WirelessMEConnection() { this((a, b) -> AEApi.instance().createGridConnection(a, b)); }
    WirelessMEConnection(Factory factory) { this.factory = factory; }
    boolean connect(IGridNode local, IGridNode source) {
        if (local == null || source == null) { detach(); return false; }
        if (this.local == local && this.source == source && exists()) return true;
        detach(); this.local = local; this.source = source;
        try { connection = factory.connect(local, source); return true; }
        catch (FailedConnection failure) { connection = null; return false; }
    }
    private boolean exists() {
        if (local == null || connection == null) return false;
        for (IGridConnection edge : local.getConnections()) if (edge == connection) return true;
        return false;
    }
    void detach() {
        if (exists()) connection.destroy();
        connection = null; local = null; source = null;
    }
}

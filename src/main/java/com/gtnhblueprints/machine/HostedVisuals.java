package com.gtnhblueprints.machine;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Server-only visual lease; the native GT active packet drives client animations. */
public final class HostedVisuals {
    public static final int LEASE_TICKS = 20;
    private HostedVisuals() {}

    public interface State {
        void matterblueprints$visual(boolean managed, boolean active, long until);
    }

    static void update(MTEMultiBlockBase machine, boolean active, long tick) {
        if (machine instanceof State) ((State) machine).matterblueprints$visual(true, active, tick + LEASE_TICKS);
    }

    static void release(MTEMultiBlockBase machine) {
        if (machine instanceof State) ((State) machine).matterblueprints$visual(false, false, 0);
    }

    static void stop(MTEMultiBlockBase machine) {
        if (machine instanceof State) ((State) machine).matterblueprints$visual(true, false, 0);
        if (machine.getBaseMetaTileEntity() != null && machine.getBaseMetaTileEntity().isActive()) {
            machine.getBaseMetaTileEntity().setActive(false);
        }
    }

    public static int displayProgress(int nativeProgress, boolean managed, boolean active,
        boolean formed, long until, long tick) {
        if (!managed) return nativeProgress;
        return active && formed && tick <= until ? 1 : 0;
    }
}

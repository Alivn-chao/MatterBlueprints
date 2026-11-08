package com.gtnhblueprints.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import com.gtnhblueprints.machine.HostedVisuals;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MixinHostedMachineVisuals implements HostedVisuals.State {
    @Unique private boolean matterblueprints$managedVisual;
    @Unique private boolean matterblueprints$activeVisual;
    @Unique private long matterblueprints$visualUntil;

    @Override
    public void matterblueprints$visual(boolean managed, boolean active, long until) {
        matterblueprints$managedVisual = managed;
        // Idle preserves the last working deadline: short recipe handoffs do not churn thousands of coil blocks.
        // A zero deadline is an explicit switch-off/release and bypasses this one-second grace.
        if (!managed || until == 0 || active) {
            matterblueprints$activeVisual = active;
            matterblueprints$visualUntil = until;
        }
    }

    // Exactly two reads: the active texture and native coil lease. runMachine's recipe reads remain untouched.
    @Redirect(method = "onPostTick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;mMaxProgresstime:I"), require = 2, allow = 2)
    private int matterblueprints$displayProgress(MTEMultiBlockBase machine) {
        if (!matterblueprints$managedVisual) return machine.mMaxProgresstime;
        IGregTechTileEntity tile = machine.getBaseMetaTileEntity();
        return HostedVisuals.displayProgress(machine.mMaxProgresstime, true, matterblueprints$activeVisual,
            machine.mMachine, matterblueprints$visualUntil, tile.getWorld().getTotalWorldTime());
    }

    @Redirect(method = "onPostTick", at = @At(value = "INVOKE",
        target = "Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;setActive(Z)V"), require = 1, allow = 1)
    private void matterblueprints$syncOnlyTransitions(IGregTechTileEntity tile, boolean active) {
        if (!matterblueprints$managedVisual || tile.isActive() != active) tile.setActive(active);
    }
}

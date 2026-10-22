package com.gtnhblueprints.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.gtnhblueprints.machine.HostedAntimatterSupport;
import com.gtnhblueprints.machine.HostedAntimatterBatch;
import net.minecraftforge.fluids.FluidStack;

import goodgenerator.blocks.tileEntity.AntimatterForge;
import goodgenerator.blocks.tileEntity.AntimatterOutputHatch;
import java.util.List;

/** Routes a hosted batch to shared central storage while retaining native antimatter chemistry. */
@Mixin(value = AntimatterForge.class, remap = false)
public abstract class MixinAntimatterForge implements HostedAntimatterSupport.Patched {

    @Shadow
    @Final
    private List<AntimatterOutputHatch> amOutputHatches;
    @Shadow private long rollingCost;
    @Shadow @Final private float[] modifiers;
    @Shadow private long guiActiveEnergy;
    @Shadow private long guiPassiveEnergy;
    @Shadow private long calculateEnergyCost(long amount) { throw new AssertionError(); }
    @Shadow private long calculateEnergyContainmentCost(long amount) { throw new AssertionError(); }

    @Override
    public List<AntimatterOutputHatch> matterblueprints$getPhysicalHatches() {
        return amOutputHatches;
    }

    @Override
    public long matterblueprints$getRollingCost() { return rollingCost; }

    @Override
    public float matterblueprints$getMagneticModifier() { return modifiers[0]; }

    @Override
    public void matterblueprints$restoreContainment(long cost, float magnetic) {
        rollingCost = cost;
        modifiers[0] = magnetic;
    }

    @Redirect(method = "checkProcessing", at = @At(value = "INVOKE", target = "Ljava/lang/Math;ceil(D)D"), require = 4)
    private double matterblueprints$upgradeFluids(double amount) {
        return Math.ceil(amount) * HostedAntimatterSupport.machines((AntimatterForge) (Object) this);
    }

    @ModifyVariable(method = "distributeAntimatterToHatch", at = @At("STORE"), index = 11, require = 1)
    private int matterblueprints$batchChange(int change) {
        AntimatterForge forge = (AntimatterForge) (Object) this;
        return HostedAntimatterSupport.isHosted(forge) ? HostedAntimatterBatch.scaleChange(change, HostedAntimatterSupport.machines(forge)) : change;
    }

    @Redirect(method = "checkProcessing", at = @At(value = "INVOKE",
        target = "Lgoodgenerator/blocks/tileEntity/AntimatterForge;calculateEnergyCost(J)J"), require = 1)
    private long matterblueprints$batchEnergy(AntimatterForge forge, long amount) {
        guiActiveEnergy = HostedAntimatterBatch.scaleCost(calculateEnergyCost(amount), HostedAntimatterSupport.machines(forge));
        return guiActiveEnergy;
    }

    @Redirect(method = "onPostTick", at = @At(value = "INVOKE",
        target = "Lgoodgenerator/blocks/tileEntity/AntimatterForge;calculateEnergyContainmentCost(J)J"), require = 1)
    private long matterblueprints$batchContainment(AntimatterForge forge, long amount) {
        guiPassiveEnergy = HostedAntimatterBatch.scaleCost(calculateEnergyContainmentCost(amount), HostedAntimatterSupport.machines(forge));
        return guiPassiveEnergy;
    }

    @Redirect(method = "distributeAntimatterToHatch", at = @At(value = "INVOKE",
        target = "Lgoodgenerator/blocks/tileEntity/AntimatterOutputHatch;fill(Lnet/minecraftforge/fluids/FluidStack;Z)I"), require = 1)
    private int matterblueprints$spillOutput(AntimatterOutputHatch hatch, FluidStack fluid, boolean commit) {
        return HostedAntimatterSupport.fill((AntimatterForge) (Object) this, hatch, fluid, commit);
    }

    @Redirect(method = { "onPostTick", "checkProcessing", "calculateContainedAntimatter", "decimateAntimatter" },
        at = @At(value = "FIELD",
            target = "Lgoodgenerator/blocks/tileEntity/AntimatterForge;amOutputHatches:Ljava/util/List;"), require = 1)
    private List<AntimatterOutputHatch> matterblueprints$centralHatches(AntimatterForge forge) {
        return HostedAntimatterSupport.hatches(forge, amOutputHatches);
    }

    @Redirect(method = "onPostTick", at = @At(value = "INVOKE",
        target = "Lgoodgenerator/blocks/tileEntity/AntimatterForge;drainEnergyInput(J)Z"), require = 1)
    private boolean matterblueprints$containmentPower(AntimatterForge forge, long amount) {
        return HostedAntimatterSupport.drain(forge, amount, false);
    }

    @Redirect(method = "checkProcessing", at = @At(value = "INVOKE",
        target = "Lgoodgenerator/blocks/tileEntity/AntimatterForge;drainEnergyInput(J)Z"), require = 1)
    private boolean matterblueprints$cyclePower(AntimatterForge forge, long amount) {
        return HostedAntimatterSupport.drain(forge, amount, true);
    }
}

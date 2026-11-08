package com.gtnhblueprints.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import gregtech.common.blocks.BlockMachines;
import com.gtnhblueprints.machine.HostedBindingSupport;

@Mixin(BlockMachines.class)
public abstract class MixinGTHostedBindingRemoval {
    @Inject(method = "breakBlock", at = @At("HEAD"), require = 1)
    private void matterblueprints$remove(World world, int x, int y, int z, Block block, int metadata, CallbackInfo ci) {
        HostedBindingSupport.removed(world, x, y, z);
    }
}

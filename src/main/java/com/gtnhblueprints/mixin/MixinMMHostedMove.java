package com.gtnhblueprints.mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.gtnhblueprints.machine.HostedBindingMove;
import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;
import com.recursive_pineapple.matter_manipulator.common.building.PendingMove;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;

/** Native same-dimension moves and GTBP cross-dimension moves share this entry point. */
@Mixin(value = PendingMove.class, remap = false)
public abstract class MixinMMHostedMove {
    @WrapMethod(method = "moveBlock")
    private static boolean matterblueprints$move(PendingMove move, World world, Location source,
        BlockSpec sourceBlock, Location target, BlockSpec targetBlock, Operation<Boolean> original) {
        return HostedBindingMove.move(world, source, target,
            () -> original.call(move, world, source, sourceBlock, target, targetBlock));
    }
}

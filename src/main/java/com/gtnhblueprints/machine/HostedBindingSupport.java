package com.gtnhblueprints.machine;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext;

/** MM copies only the center identity, never running jobs, fuel, outputs or milestones. */
public final class HostedBindingSupport {
    private HostedBindingSupport() {}

    public interface Trait {
        String matterblueprints$getHost();
        void matterblueprints$setHost(String id);
    }

    public static String key(IGregTechTileEntity tile) {
        return key(tile.getWorld(), tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    private static String key(World world, int x, int y, int z) {
        return world.provider.dimensionId + ":" + x + ":" + y + ":" + z;
    }

    public static String capture(IGregTechTileEntity tile) {
        if (tile.getWorld() == null || tile.getWorld().isRemote
            || !(tile.getMetaTileEntity() instanceof MTEMultiBlockBase)
            || tile.getMetaTileEntity() instanceof MTEHostedMachineController) return null;
        return HostedBindingData.get(tile.getWorld()).owner(key(tile));
    }

    public static void apply(String id, IBlockApplyContext context) {
        if (id == null || context.getWorld().isRemote) return;
        TileEntity tile = context.getTileEntity();
        if (!(tile instanceof IGregTechTileEntity)) return;
        IGregTechTileEntity gt = (IGregTechTileEntity) tile;
        if (!(gt.getMetaTileEntity() instanceof MTEMultiBlockBase)
            || gt.getMetaTileEntity() instanceof MTEHostedMachineController) return;
        if (!HostedBindingData.get(context.getWorld()).bind(id, key(gt))) {
            context.warn(new ChatComponentTranslation("matterblueprints.binder.copy_unavailable"));
        }
    }

    /** Called by the actual block removal path, not invalidate/chunk unload or a cancellable break event. */
    public static void removed(World world, int x, int y, int z) {
        if (world.isRemote) return;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity)) return;
        IGregTechTileEntity gt = (IGregTechTileEntity) tile;
        if (!(gt.getMetaTileEntity() instanceof MTEMultiBlockBase)) return;
        HostedBindingData data = HostedBindingData.get(world);
        MTEMultiBlockBase machine = (MTEMultiBlockBase) gt.getMetaTileEntity();
        if (machine instanceof MTEHostedMachineController) {
            ((MTEHostedMachineController) machine).removeBindingIdentity();
        } else {
            String owner = data.owner(key(world, x, y, z));
            String location = owner == null ? null : data.location(owner);
            data.unbind(key(world, x, y, z));
            TileEntity center = resolve(location);
            if (center instanceof IGregTechTileEntity) {
                Object meta = ((IGregTechTileEntity) center).getMetaTileEntity();
                if (meta instanceof MTEHostedMachineController) {
                    ((MTEHostedMachineController) meta).removeDestroyedBinding(machine);
                }
            }
        }
    }

    static TileEntity resolve(String location) {
        if (location == null) return null;
        String[] parts = location.split(":", -1);
        if (parts.length != 4) return null;
        try {
            World world = DimensionManager.getWorld(Integer.parseInt(parts[0]));
            int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
            return world != null && world.blockExists(x, y, z) ? world.getTileEntity(x, y, z) : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** Only loaded, empty/non-controller locations are stale; an unloaded world is unknown. */
    static boolean isMissingMachine(String location) {
        String[] parts = location.split(":", -1);
        if (parts.length != 4) return false;
        try {
            World world = DimensionManager.getWorld(Integer.parseInt(parts[0]));
            int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
            if (world == null || !world.blockExists(x, y, z)) return false;
            TileEntity tile = world.getTileEntity(x, y, z);
            return !(tile instanceof IGregTechTileEntity)
                || !(((IGregTechTileEntity) tile).getMetaTileEntity() instanceof MTEMultiBlockBase);
        } catch (NumberFormatException invalid) {
            return false;
        }
    }
}

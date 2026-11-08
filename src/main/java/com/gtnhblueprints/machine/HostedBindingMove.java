package com.gtnhblueprints.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;

/** Runs only during MM movement; no per-tick search or forced chunk loading. */
public final class HostedBindingMove {
    private HostedBindingMove() {}

    public static boolean move(World world, Location source, Location target, BooleanSupplier operation) {
        if (world.isRemote) return operation.getAsBoolean();
        World sourceWorld = source.worldId == world.provider.dimensionId ? world : source.getWorld();
        World targetWorld = target.worldId == world.provider.dimensionId ? world : target.getWorld();
        if (sourceWorld == null || targetWorld == null) return operation.getAsBoolean();
        MTEMultiBlockBase sourceMachine = machine(sourceWorld, source);
        MTEMultiBlockBase targetMachine = machine(targetWorld, target);
        if (sourceMachine == null && targetMachine == null) return operation.getAsBoolean();
        HostedBindingData data = HostedBindingData.get(world);
        String from = key(source), to = key(target);
        String sourceOwner = data.owner(from), targetOwner = data.owner(to);
        if (sourceOwner == null && targetOwner == null) return operation.getAsBoolean();
        Map<String, MTEHostedMachineController> centers = new HashMap<>();
        release(data, sourceOwner, sourceMachine, centers);
        release(data, targetOwner, targetMachine, centers);
        // MM removes the tile before removing its block, so ordinary breakBlock cannot
        // see it. Do not use the copy/apply hook or turn removal into a global exception.
        if (!commit(data, from, to, operation)) return false;
        for (Map.Entry<String, MTEHostedMachineController> entry : centers.entrySet()) {
            Map<String, String> moved = new HashMap<>();
            if (entry.getKey().equals(sourceOwner)) moved.put(from, to);
            if (entry.getKey().equals(targetOwner)) moved.put(to, from);
            entry.getValue().relocateHostedBindings(moved);
        }
        return true;
    }

    static boolean commit(HostedBindingData data, String from, String to, BooleanSupplier operation) {
        if (!operation.getAsBoolean()) return false;
        data.move(from, to);
        return true;
    }

    private static void release(HostedBindingData data, String id, MTEMultiBlockBase machine,
        Map<String, MTEHostedMachineController> centers) {
        if (id == null || machine == null) return;
        TileEntity tile = HostedBindingSupport.resolve(data.location(id));
        if (!(tile instanceof IGregTechTileEntity)) return;
        Object meta = ((IGregTechTileEntity) tile).getMetaTileEntity();
        if (meta instanceof MTEHostedMachineController) {
            MTEHostedMachineController center = (MTEHostedMachineController) meta;
            // Restore native progress before MM serializes the controller; deferred
            // equivalent jobs remain at the center and are re-keyed after success.
            center.releaseHostedMachine(machine);
            centers.put(id, center);
        }
    }

    private static MTEMultiBlockBase machine(World world, Location location) {
        if (!world.blockExists(location.x, location.y, location.z)) return null;
        TileEntity tile = world.getTileEntity(location.x, location.y, location.z);
        if (!(tile instanceof IGregTechTileEntity)) return null;
        Object meta = ((IGregTechTileEntity) tile).getMetaTileEntity();
        return meta instanceof MTEMultiBlockBase && !(meta instanceof MTEHostedMachineController)
            ? (MTEMultiBlockBase) meta : null;
    }

    private static String key(Location location) {
        return location.worldId + ":" + location.x + ":" + location.y + ":" + location.z;
    }
}

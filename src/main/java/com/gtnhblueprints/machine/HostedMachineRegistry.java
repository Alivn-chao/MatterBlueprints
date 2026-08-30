package com.gtnhblueprints.machine;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public final class HostedMachineRegistry {

    public static final HostedMachineRegistry INSTANCE = new HostedMachineRegistry();

    private static final Map<MTEMultiBlockBase, MTEHostedMachineController> CLAIMS =
        new IdentityHashMap<MTEMultiBlockBase, MTEHostedMachineController>();

    private HostedMachineRegistry() {}

    static synchronized boolean claim(MTEMultiBlockBase machine, MTEHostedMachineController owner) {
        MTEHostedMachineController existing = CLAIMS.get(machine);
        if (existing != null && existing != owner) return false;
        CLAIMS.put(machine, owner);
        return true;
    }

    static synchronized void release(MTEMultiBlockBase machine, MTEHostedMachineController owner) {
        if (CLAIMS.get(machine) == owner) CLAIMS.remove(machine);
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.world.isRemote) return;
        releaseMatching(
            event.world.provider.dimensionId,
            event.getChunk().xPosition,
            event.getChunk().zPosition,
            false);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        releaseMatching(event.world.provider.dimensionId, 0, 0, true);
    }

    private static synchronized void releaseMatching(int dimension, int chunkX, int chunkZ, boolean wholeDimension) {
        Map<MTEMultiBlockBase, MTEHostedMachineController> snapshot =
            new IdentityHashMap<MTEMultiBlockBase, MTEHostedMachineController>(CLAIMS);
        for (Map.Entry<MTEMultiBlockBase, MTEHostedMachineController> entry : snapshot.entrySet()) {
            IGregTechTileEntity tile = entry.getKey().getBaseMetaTileEntity();
            if (tile == null || tile.getWorld() == null || tile.getWorld().provider.dimensionId != dimension) continue;
            if (!wholeDimension && (tile.getXCoord() >> 4) != chunkX) continue;
            if (!wholeDimension && (tile.getZCoord() >> 4) != chunkZ) continue;
            entry.getValue().releaseHostedMachine(entry.getKey());
        }
    }
}

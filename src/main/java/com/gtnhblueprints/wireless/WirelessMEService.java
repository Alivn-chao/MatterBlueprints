package com.gtnhblueprints.wireless;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class WirelessMEService {
    public static final WirelessMEService INSTANCE = new WirelessMEService();
    private static final Map<WirelessMEBinding, Boolean> LIVE = new WeakHashMap<>();
    private int ticks;
    public static void track(WirelessMEBinding binding) { LIVE.put(binding, Boolean.TRUE); }
    public static void untrack(WirelessMEBinding binding) { LIVE.remove(binding); }
    public static WirelessMEEndpoint endpoint(TileEntity tile) {
        if (tile instanceof WirelessMEEndpoint) return (WirelessMEEndpoint) tile;
        if (tile instanceof IGregTechTileEntity && ((IGregTechTileEntity) tile).getMetaTileEntity() instanceof WirelessMEEndpoint)
            return (WirelessMEEndpoint) ((IGregTechTileEntity) tile).getMetaTileEntity();
        return null;
    }
    public static WirelessMEEndpoint source(WirelessMEData.Channel channel) {
        WirelessMEData.Location p = channel.source;
        if (p == null) return null;
        World world = DimensionManager.getWorld(p.dimension);
        if (world == null || !world.getChunkProvider().chunkExists(p.x >> 4, p.z >> 4)) return null;
        WirelessMEEndpoint endpoint = endpoint(world.getTileEntity(p.x, p.y, p.z));
        return endpoint != null && endpoint.wirelessSource() && !endpoint.getProxy().getConnectableSides().isEmpty() && channel.id.equals(endpoint.wireless().channelId())
            && WirelessMEData.sameTeam(channel.owner, endpoint.wireless().owner()) ? endpoint : null;
    }
    public static void detachMembers(String id) {
        for (WirelessMEBinding binding : new ArrayList<>(LIVE.keySet())) if (id.equals(binding.channelId())) binding.detach();
    }
    public static int[] count(String id) {
        int[] counts = new int[2];
        for (WirelessMEBinding binding : new ArrayList<>(LIVE.keySet())) {
            if (!id.equals(binding.channelId()) || !loaded(binding)) continue;
            counts[binding.endpoint.wirelessSource() ? 0 : 1]++;
        }
        return counts;
    }
    private static boolean loaded(WirelessMEBinding binding) {
        TileEntity tile = binding.endpoint.wirelessTile();
        return tile != null && !tile.isInvalid() && tile.getWorldObj() != null
            && tile.getWorldObj().getChunkProvider().chunkExists(tile.xCoord >> 4, tile.zCoord >> 4)
            && endpoint(tile.getWorldObj().getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord)) == binding.endpoint;
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        for (WirelessMEBinding binding : new ArrayList<>(LIVE.keySet())) {
            if (!loaded(binding)) { binding.detach(); untrack(binding); continue; }
            binding.tick();
        }
    }
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST) public void placed(BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || event.isCanceled()) return;
        WirelessMEEndpoint endpoint = endpoint(event.world.getTileEntity(event.x, event.y, event.z));
        if (endpoint != null) {
            try { endpoint.wireless().placed(event.player); }
            catch (IllegalArgumentException failure) { event.player.addChatMessage(new net.minecraft.util.ChatComponentTranslation(failure.getMessage())); }
        }
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (!event.world.isRemote) for (WirelessMEBinding binding : new ArrayList<>(LIVE.keySet()))
            if (binding.endpoint.wirelessTile() == null || binding.endpoint.wirelessTile().getWorldObj() == event.world) {
                binding.detach(); untrack(binding);
            }
    }
}

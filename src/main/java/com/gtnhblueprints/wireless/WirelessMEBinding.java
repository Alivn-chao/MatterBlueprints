package com.gtnhblueprints.wireless;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import appeng.api.networking.IGridNode;
import appeng.me.GridAccessException;
import com.gtnhblueprints.wireless.WirelessMEData.Channel;
import com.gtnhblueprints.wireless.WirelessMEData.Location;

/** One wireless edge per terminal, directly to the controller: no shared 8/32-channel transport node. */
public final class WirelessMEBinding {
    final WirelessMEEndpoint endpoint;
    private String channelId = "";
    private UUID owner;
    private final WirelessMEConnection connection = new WirelessMEConnection();
    private String state = "standalone";
    public WirelessMEBinding(WirelessMEEndpoint endpoint) { this.endpoint = endpoint; }
    public String channelId() { return channelId; }
    public UUID owner() { return owner; }
    public String state() { return state; }
    public Location location() {
        TileEntity tile = endpoint.wirelessTile();
        return new Location(tile.getWorldObj().provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord);
    }
    private WirelessMEData data() { return WirelessMEData.get(endpoint.wirelessTile().getWorldObj()); }
    public void placed(EntityPlayer player) {
        detach(); channelId = ""; owner = player.getUniqueID();
        endpoint.getProxy().setOwner(player);
        // Ordinary placement never reuses a channel copied on the held block item.
        if (player.isSneaking()) {
            String favorite = data().favorite(owner);
            if (!favorite.isEmpty()) {
                join(player, favorite);
                Channel channel = data().get(channelId);
                if (channel != null) player.addChatMessage(new net.minecraft.util.ChatComponentTranslation(
                    "matterblueprints.wireless.placement_connected", channel.name));
            } else player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("matterblueprints.wireless.no_favorite"));
        }
        dirty(); WirelessMEService.track(this);
    }
    public boolean canManage(EntityPlayer player) {
        return owner == null || WirelessMEData.sameTeam(owner, player.getUniqueID())
            || player.canCommandSenderUseCommand(2, "matterblueprints");
    }
    public void join(EntityPlayer player, String id) {
        if (!canManage(player)) throw new IllegalArgumentException("matterblueprints.wireless.no_permission");
        Channel channel = data().require(player.getUniqueID(), id);
        if (owner != null && !WirelessMEData.sameTeam(owner, channel.owner)) throw new IllegalArgumentException("matterblueprints.wireless.no_permission");
        if (endpoint.wirelessSource() && channel.source != null && !channel.source.equals(location()))
            throw new IllegalArgumentException("matterblueprints.wireless.source_exists");
        if (id.equals(channelId) && owner != null) {
            if (endpoint.wirelessSource()) data().claimSource(owner, id, location());
            tick(); return;
        }
        leaveCurrentSource(); detach();
        if (owner == null) owner = player.getUniqueID();
        channelId = id;
        if (endpoint.wirelessSource()) data().claimSource(owner, id, location());
        dirty(); WirelessMEService.track(this); tick();
    }
    public void disconnect(EntityPlayer player) {
        if (!canManage(player)) throw new IllegalArgumentException("matterblueprints.wireless.no_permission");
        leaveCurrentSource(); detach(); channelId = ""; state = "standalone"; dirty();
    }
    private void leaveCurrentSource() {
        if (endpoint.wirelessSource() && !channelId.isEmpty()) {
            WirelessMEService.detachMembers(channelId);
            data().releaseSource(channelId, location());
        }
    }
    public void removed() { leaveCurrentSource(); detach(); WirelessMEService.untrack(this); }
    void dirty() { TileEntity tile = endpoint.wirelessTile(); if (tile != null) tile.markDirty(); }
    public void save(NBTTagCompound tag) {
        NBTTagCompound wireless = new NBTTagCompound(); wireless.setString("channel", channelId);
        if (owner != null) wireless.setString("owner", owner.toString()); tag.setTag("mbWirelessME", wireless);
    }
    public void load(NBTTagCompound tag) {
        detach();
        NBTTagCompound wireless = tag.getCompoundTag("mbWirelessME"); channelId = wireless.getString("channel");
        owner = wireless.hasKey("owner") ? UUID.fromString(wireless.getString("owner")) : null;
    }
    public void detach() { connection.detach(); }
    public void tick() {
        if (channelId.isEmpty()) { detach(); state = "standalone"; return; }
        Channel channel = data().get(channelId);
        if (channel == null) { detach(); channelId = ""; state = "standalone"; dirty(); return; }
        if (!WirelessMEData.sameTeam(channel.owner, owner)) { detach(); state = "denied"; return; }
        if (endpoint.wirelessSource()) {
            try {
                state = location().equals(channel.source) && !endpoint.getProxy().getConnectableSides().isEmpty() && endpoint.getProxy().isActive()
                    && endpoint.getProxy().getPath().getControllerState() == appeng.api.networking.pathing.ControllerState.CONTROLLER_ONLINE
                    ? "online" : "offline";
            } catch (GridAccessException unavailable) { state = "offline"; }
            return;
        }
        WirelessMEEndpoint source = WirelessMEService.source(channel);
        IGridNode target = source == null ? null : source.getProxy().getNode();
        IGridNode local = endpoint.getProxy().getNode();
        if (target == null || local == null) { detach(); state = "offline"; return; }
        if (!connection.connect(local, target)) { state = "denied"; return; }
        state = endpoint.getProxy().isActive() ? "online" : "offline";
    }
}

package com.gtnhblueprints.wireless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** World-owned channel metadata. No inventories or live grid nodes are serialized here. */
public final class WirelessMEData extends WorldSavedData {
    public static final String KEY = "matterblueprints_wireless_me";
    private final Map<String, Channel> channels = new LinkedHashMap<>();
    private final Map<UUID, String> favorites = new HashMap<>();
    public WirelessMEData() { this(KEY); }
    public WirelessMEData(String name) { super(name); }
    public static final class Channel {
        public final String id;
        public final UUID owner;
        public final String name;
        public Location source;
        Channel(String id, UUID owner, String name) { this.id = id; this.owner = owner; this.name = name; }
    }
    public static final class Location {
        public final int dimension, x, y, z;
        public Location(int dimension, int x, int y, int z) { this.dimension = dimension; this.x = x; this.y = y; this.z = z; }
        public NBTTagCompound save() {
            NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("dim", dimension); tag.setInteger("x", x);
            tag.setInteger("y", y); tag.setInteger("z", z); return tag;
        }
        static Location load(NBTTagCompound tag) { return new Location(tag.getInteger("dim"), tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Location)) return false;
            Location l = (Location) other; return dimension == l.dimension && x == l.x && y == l.y && z == l.z;
        }
        @Override public int hashCode() { return Objects.hash(dimension, x, y, z); }
    }
    public static WirelessMEData get(World world) {
        if (world == null || world.isRemote) throw new IllegalStateException("Wireless channels are server-owned");
        World root = DimensionManager.getWorld(0); if (root == null) root = world;
        WirelessMEData data = (WirelessMEData) root.mapStorage.loadData(WirelessMEData.class, KEY);
        if (data == null) { data = new WirelessMEData(); root.mapStorage.setData(KEY, data); data.markDirty(); }
        return data;
    }
    public Channel create(UUID owner, String rawName) {
        if (owner == null) throw new IllegalArgumentException("owner");
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > 48 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("matterblueprints.wireless.invalid_name");
        Channel channel = new Channel(UUID.randomUUID().toString(), owner, name);
        channels.put(channel.id, channel); markDirty(); return channel;
    }
    public Channel get(String id) { return channels.get(id); }
    public static boolean sameTeam(UUID a, UUID b) {
        return a != null && b != null && (a.equals(b)
            || SpaceProjectManager.getLeader(a).equals(SpaceProjectManager.getLeader(b)));
    }
    public Channel require(UUID owner, String id) {
        Channel channel = channels.get(id);
        if (channel == null || !sameTeam(channel.owner, owner)) throw new IllegalArgumentException("matterblueprints.wireless.no_permission");
        return channel;
    }
    public List<Channel> visible(UUID player) {
        List<Channel> result = new ArrayList<>();
        for (Channel channel : channels.values()) if (sameTeam(channel.owner, player)) result.add(channel);
        return result;
    }
    public String favorite(UUID player) {
        String id = favorites.getOrDefault(player, "");
        Channel channel = get(id);
        return channel != null && sameTeam(channel.owner, player) ? id : "";
    }
    public void toggleFavorite(UUID player, String id) {
        require(player, id);
        if (id.equals(favorites.get(player))) favorites.remove(player); else favorites.put(player, id);
        markDirty();
    }
    public void delete(UUID player, String id) {
        Channel channel = require(player, id);
        if (!channel.owner.equals(player)) throw new IllegalArgumentException("matterblueprints.wireless.no_permission");
        channels.remove(id); favorites.values().removeIf(id::equals); markDirty();
    }
    public void claimSource(UUID owner, String id, Location location) {
        Channel channel = require(owner, id);
        if (channel.source != null && !channel.source.equals(location))
            throw new IllegalArgumentException("matterblueprints.wireless.source_exists");
        channel.source = location; markDirty();
    }
    public void releaseSource(String id, Location location) {
        Channel channel = get(id);
        if (channel != null && location.equals(channel.source)) { channel.source = null; markDirty(); }
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        if (tag.getInteger("version") > 1) throw new IllegalStateException("Unsupported wireless ME data version");
        channels.clear(); favorites.clear();
        NBTTagList saved = tag.getTagList("channels", 10);
        for (int i = 0; i < saved.tagCount(); i++) {
            NBTTagCompound entry = saved.getCompoundTagAt(i);
            Channel channel = new Channel(entry.getString("id"), UUID.fromString(entry.getString("owner")), entry.getString("name"));
            if (entry.hasKey("source")) channel.source = Location.load(entry.getCompoundTag("source"));
            channels.put(channel.id, channel);
        }
        saved = tag.getTagList("favorites", 10);
        for (int i = 0; i < saved.tagCount(); i++) {
            NBTTagCompound entry = saved.getCompoundTagAt(i);
            UUID owner = UUID.fromString(entry.getString("owner")); String id = entry.getString("id");
            Channel channel = get(id);
            if (channel != null) favorites.put(owner, id);
        }
    }
    @Override public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("version", 1);
        NBTTagList saved = new NBTTagList();
        for (Channel channel : channels.values()) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setString("id", channel.id);
            entry.setString("owner", channel.owner.toString()); entry.setString("name", channel.name);
            if (channel.source != null) entry.setTag("source", channel.source.save()); saved.appendTag(entry);
        }
        tag.setTag("channels", saved); saved = new NBTTagList();
        for (Map.Entry<UUID, String> favorite : favorites.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setString("owner", favorite.getKey().toString());
            entry.setString("id", favorite.getValue()); saved.appendTag(entry);
        }
        tag.setTag("favorites", saved);
    }
}

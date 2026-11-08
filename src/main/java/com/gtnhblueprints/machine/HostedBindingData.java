package com.gtnhblueprints.machine;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/** Persistent reverse index. Unloading a chunk never changes ownership. */
public final class HostedBindingData extends WorldSavedData {
    private static final String KEY = "matterblueprints_host_bindings";
    private final Map<String, String> locations = new HashMap<>();
    private final Map<String, Set<String>> members = new HashMap<>();
    private final Map<String, String> owners = new HashMap<>();

    public HostedBindingData() { this(KEY); }
    public HostedBindingData(String name) { super(name); }

    public static HostedBindingData get(World world) {
        if (world == null || world.isRemote) throw new IllegalStateException("Bindings are server-owned");
        World root = DimensionManager.getWorld(0);
        if (root == null) root = world;
        HostedBindingData data = (HostedBindingData) root.mapStorage.loadData(HostedBindingData.class, KEY);
        if (data == null) {
            data = new HostedBindingData();
            root.mapStorage.setData(KEY, data);
            data.markDirty();
        }
        return data;
    }

    public void register(String id, String location) {
        if (location.equals(locations.get(id))) return;
        locations.put(id, location);
        members.computeIfAbsent(id, ignored -> new HashSet<>());
        markDirty();
    }

    public String location(String id) { return locations.get(id); }
    public String owner(String machine) { return owners.get(machine); }

    public Set<String> members(String id) {
        Set<String> result = members.get(id);
        return result == null ? Collections.emptySet() : Collections.unmodifiableSet(result);
    }

    /** Idempotent: MM may apply traits repeatedly; never toggle or steal another center's machine. */
    public boolean bind(String id, String machine) {
        if (!locations.containsKey(id) || locations.get(id).equals(machine)) return false;
        String previous = owners.get(machine);
        if (previous != null) return previous.equals(id);
        owners.put(machine, id);
        members.get(id).add(machine);
        markDirty();
        return true;
    }

    public void unbind(String machine) {
        String id = owners.remove(machine);
        if (id == null) return;
        members.get(id).remove(machine);
        markDirty();
    }

    /** MM swaps two blocks, including when one is air. Update both ends atomically. */
    public void move(String source, String target) {
        if (source.equals(target)) return;
        String sourceOwner = owners.get(source), targetOwner = owners.get(target);
        if (sourceOwner == null && targetOwner == null) return;
        unbind(source);
        unbind(target);
        if (sourceOwner != null) {
            owners.put(target, sourceOwner);
            members.get(sourceOwner).add(target);
        }
        if (targetOwner != null) {
            owners.put(source, targetOwner);
            members.get(targetOwner).add(source);
        }
        markDirty();
    }

    public void removeHost(String id) {
        Set<String> removed = members.remove(id);
        if (removed == null) return;
        for (String machine : removed) owners.remove(machine);
        locations.remove(id);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        locations.clear(); members.clear(); owners.clear();
        NBTTagList hosts = tag.getTagList("hosts", 10);
        for (int i = 0; i < hosts.tagCount(); i++) {
            NBTTagCompound host = hosts.getCompoundTagAt(i);
            String id = host.getString("id");
            String location = host.getString("location");
            if (id.isEmpty() || location.isEmpty()) continue;
            register(id, location);
            NBTTagList machines = host.getTagList("members", 8);
            for (int j = 0; j < machines.tagCount(); j++) bind(id, machines.getStringTagAt(j));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList hosts = new NBTTagList();
        for (Map.Entry<String, String> entry : locations.entrySet()) {
            NBTTagCompound host = new NBTTagCompound();
            host.setString("id", entry.getKey());
            host.setString("location", entry.getValue());
            NBTTagList machines = new NBTTagList();
            for (String machine : members.get(entry.getKey())) machines.appendTag(new NBTTagString(machine));
            host.setTag("members", machines);
            hosts.appendTag(host);
        }
        tag.setTag("hosts", hosts);
    }
}

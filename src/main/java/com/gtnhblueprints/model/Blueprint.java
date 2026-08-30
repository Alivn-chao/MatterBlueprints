package com.gtnhblueprints.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import com.gtnhblueprints.MatterBlueprints;
import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.RegionAnalysis;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

public final class Blueprint {

    public static final int SCHEMA_VERSION = 2;

    public int schemaVersion = SCHEMA_VERSION;
    public String name;
    public String authorName;
    public String authorUuid;
    public String createdAt;
    public String sourceBlueprintName;
    public String targetGtnhVersion = MatterBlueprints.TARGET_GTNH;
    public String gtnhCoreVersion;
    public String matterManipulatorVersion;
    public String gregTechVersion;
    public int sizeX;
    public int sizeY;
    public int sizeZ;
    public Integer deltaX;
    public Integer deltaY;
    public Integer deltaZ;
    public Map<String, String> requiredMods = new LinkedHashMap<>();
    public List<BlueprintBlock> blocks = new ArrayList<>();
    public List<BlueprintReplacement> replacements = new ArrayList<>();
    public transient int omittedUnsupportedBlocks;

    public static Blueprint capture(String name, EntityPlayer player, RegionAnalysis analysis) {
        return capture(name, player, analysis, null);
    }

    public static Blueprint capture(String name, EntityPlayer player, RegionAnalysis analysis, Location sourceOrigin) {
        Blueprint blueprint = new Blueprint();
        blueprint.name = name;
        blueprint.authorName = player.getCommandSenderName();
        blueprint.authorUuid = player.getUniqueID().toString();
        blueprint.createdAt = Instant.now().toString();
        blueprint.sizeX = Math.abs(analysis.deltas.x) + 1;
        blueprint.sizeY = Math.abs(analysis.deltas.y) + 1;
        blueprint.sizeZ = Math.abs(analysis.deltas.z) + 1;
        blueprint.deltaX = analysis.deltas.x;
        blueprint.deltaY = analysis.deltas.y;
        blueprint.deltaZ = analysis.deltas.z;
        blueprint.gtnhCoreVersion = modVersion("dreamcraft");
        blueprint.matterManipulatorVersion = modVersion("matter-manipulator");
        blueprint.gregTechVersion = modVersion("gregtech");

        for (PendingBlock pending : analysis.blocks) {
            net.minecraft.tileentity.TileEntity sourceTile = sourceOrigin == null ? null
                : player.worldObj.getTileEntity(
                    sourceOrigin.x + pending.x,
                    sourceOrigin.y + pending.y,
                    sourceOrigin.z + pending.z);
            BlueprintBlock block = BlueprintBlock.capture(pending, sourceTile);
            if (block == null) {
                blueprint.omittedUnsupportedBlocks++;
                continue;
            }
            blueprint.blocks.add(block);
            String modId = block.spec.getObjectId().modId;
            if (!"minecraft".equals(modId)) blueprint.requiredMods.put(modId, modVersion(modId));
        }
        return blueprint;
    }

    public List<PendingBlock> instantiate(int worldId) {
        List<PendingBlock> result = new ArrayList<>(blocks.size());
        for (BlueprintBlock block : blocks) result.add(block.instantiate(worldId));
        return result;
    }

    private static String modVersion(String modId) {
        ModContainer mod = Loader.instance().getIndexedModList().get(modId);
        return mod == null ? "missing" : mod.getVersion();
    }

    public UUID authorUuid() {
        try {
            return UUID.fromString(authorUuid);
        } catch (RuntimeException ignored) {
            return new UUID(0, 0);
        }
    }
}

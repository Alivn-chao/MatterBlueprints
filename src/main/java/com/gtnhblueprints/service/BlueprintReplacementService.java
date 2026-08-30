package com.gtnhblueprints.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnhblueprints.io.BlueprintIO;
import com.gtnhblueprints.io.BlueprintJson;
import com.gtnhblueprints.model.Blueprint;
import com.gtnhblueprints.model.BlueprintBlock;
import com.gtnhblueprints.model.BlueprintReplacement;
import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

public final class BlueprintReplacementService {

    private BlueprintReplacementService() {}

    public static List<BlockGroup> groups(Blueprint blueprint) {
        Map<String, BlockGroup> groups = new LinkedHashMap<>();
        if (blueprint.blocks == null) return new ArrayList<>();
        for (BlueprintBlock block : blueprint.blocks) {
            if (block == null || block.spec == null || block.spec.isAir()) continue;
            String key = key(block.spec);
            BlockGroup group = groups.get(key);
            if (group == null) {
                group = new BlockGroup(key, block.spec.clone(), displayName(block.spec));
                groups.put(key, group);
            }
            group.count++;
        }
        List<BlockGroup> result = new ArrayList<>(groups.values());
        result.sort((left, right) -> left.displayName.compareToIgnoreCase(right.displayName));
        return result;
    }

    public static BlockSpec replacementFrom(ItemStack stack) {
        if (stack == null || stack.getItem() == null) throw new IllegalArgumentException("请先在替换槽放入目标方块");
        BlockSpec spec = new BlockSpec().setObject(stack.copy());
        spec.populate();
        Block block = spec.getBlock();
        if (block == null || block == Blocks.air || spec.isAir()) {
            throw new IllegalArgumentException("替换槽中的物品不能由物质操纵者作为方块放置");
        }
        return spec;
    }

    public static Blueprint apply(
        Blueprint source,
        String outputName,
        String authorName,
        String authorUuid,
        Map<String, BlockSpec> replacements
    ) throws IOException {
        Blueprint output = BlueprintIO.decode(BlueprintIO.encode(source));
        output.schemaVersion = Blueprint.SCHEMA_VERSION;
        output.name = BlueprintIO.safeName(outputName);
        output.sourceBlueprintName = source.sourceBlueprintName == null ? source.name : source.sourceBlueprintName;
        output.authorName = authorName;
        output.authorUuid = authorUuid;
        output.createdAt = Instant.now().toString();
        if (output.replacements == null) output.replacements = new ArrayList<>();

        Map<String, Integer> affected = new LinkedHashMap<>();
        Map<String, BlockSpec> sources = new LinkedHashMap<>();
        for (BlueprintBlock block : output.blocks) {
            if (block == null || block.spec == null) continue;
            String sourceKey = key(block.spec);
            BlockSpec target = replacements.get(sourceKey);
            if (target == null) continue;
            BlockSpec original = block.spec;
            block.spec = target.clone();
            if (!original.getObjectId().equals(target.getObjectId())) clearIncompatibleTraits(block);
            sources.put(sourceKey, original);
            affected.put(sourceKey, affected.getOrDefault(sourceKey, 0) + 1);
        }
        for (Map.Entry<String, Integer> entry : affected.entrySet()) {
            output.replacements.add(
                new BlueprintReplacement(sources.get(entry.getKey()), replacements.get(entry.getKey()), entry.getValue()));
        }
        rebuildRequiredMods(output);
        return output;
    }

    public static String key(BlockSpec spec) {
        return BlueprintJson.GSON.toJson(spec, BlockSpec.class);
    }

    public static String displayName(BlockSpec spec) {
        try {
            String display = spec.getDisplayName();
            if (display != null && !display.trim().isEmpty()) return display;
        } catch (RuntimeException ignored) {}
        return spec.getObjectId() + ":" + spec.getItemMeta();
    }

    private static void clearIncompatibleTraits(BlueprintBlock block) {
        block.gt = null;
        block.ae = null;
        block.architecture = null;
        block.multipart = null;
        block.carpenters = null;
        block.translocator = null;
        block.inventory = null;
    }

    private static void rebuildRequiredMods(Blueprint blueprint) {
        blueprint.requiredMods = new LinkedHashMap<>();
        for (BlueprintBlock block : blueprint.blocks) {
            if (block == null || block.spec == null || block.spec.isAir()) continue;
            String modId = block.spec.getObjectId().modId;
            if ("minecraft".equals(modId) || blueprint.requiredMods.containsKey(modId)) continue;
            ModContainer mod = Loader.instance().getIndexedModList().get(modId);
            blueprint.requiredMods.put(modId, mod == null ? "missing" : mod.getVersion());
        }
    }

    public static final class BlockGroup {

        public final String key;
        public final BlockSpec spec;
        public final String displayName;
        public int count;

        private BlockGroup(String key, BlockSpec spec, String displayName) {
            this.key = key;
            this.spec = spec;
            this.displayName = displayName;
        }
    }
}

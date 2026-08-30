package com.gtnhblueprints.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.MatterBlueprints;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

public final class CompatibilityReport {

    public final List<String> errors = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public static CompatibilityReport inspect(Blueprint blueprint) {
        CompatibilityReport report = new CompatibilityReport();
        if (blueprint.schemaVersion < 1 || blueprint.schemaVersion > Blueprint.SCHEMA_VERSION) {
            report.errors.add("不支持的蓝图格式版本: " + blueprint.schemaVersion);
        }
        if (blueprint.blocks == null || blueprint.blocks.isEmpty()) report.errors.add("蓝图没有方块");
        if (blueprint.blocks != null && blueprint.blocks.size() > BlueprintConfig.maxBlocks) {
            report.errors.add("方块数超过限制: " + blueprint.blocks.size());
        }
        if (blueprint.sizeX < 1 || blueprint.sizeY < 1 || blueprint.sizeZ < 1
            || blueprint.sizeX > BlueprintConfig.maxSpan
            || blueprint.sizeY > BlueprintConfig.maxSpan
            || blueprint.sizeZ > BlueprintConfig.maxSpan) {
            report.errors.add("蓝图尺寸无效或超过限制");
        }
        if (!MatterBlueprints.TARGET_GTNH.equals(blueprint.targetGtnhVersion)) {
            report.warnings.add("蓝图目标 GTNH 版本为 " + blueprint.targetGtnhVersion);
        }
        String installedGtnhCore = installedVersion("dreamcraft");
        if (!MatterBlueprints.TARGET_GTNH_CORE.equals(installedGtnhCore)) {
            report.warnings.add(
                "已针对 GTNewHorizonsCoreMod " + MatterBlueprints.TARGET_GTNH_CORE + " 验证，当前为 " + installedGtnhCore);
        }
        if (blueprint.gtnhCoreVersion != null && !blueprint.gtnhCoreVersion.equals(installedGtnhCore)) {
            report.warnings.add(
                "蓝图由 GTNewHorizonsCoreMod " + blueprint.gtnhCoreVersion + " 保存，当前为 " + installedGtnhCore);
        }
        String installedMm = installedVersion("matter-manipulator");
        if (!MatterBlueprints.TARGET_MM.equals(installedMm)) {
            report.errors.add("需要 Matter Manipulator " + MatterBlueprints.TARGET_MM + "，当前为 " + installedMm);
        }
        if (blueprint.matterManipulatorVersion != null && !blueprint.matterManipulatorVersion.equals(installedMm)) {
            report.warnings.add("蓝图由 Matter Manipulator " + blueprint.matterManipulatorVersion + " 保存");
        }

        if (blueprint.requiredMods != null) {
            for (Map.Entry<String, String> requirement : blueprint.requiredMods.entrySet()) {
                ModContainer installed = Loader.instance().getIndexedModList().get(requirement.getKey());
                if (installed == null) {
                    report.errors.add("缺少模组: " + requirement.getKey());
                } else if (!installed.getVersion().equals(requirement.getValue())) {
                    report.warnings.add(
                        "模组版本不同: " + requirement.getKey() + " " + requirement.getValue() + " -> " + installed.getVersion());
                }
            }
        }

        if (blueprint.blocks != null) {
            for (int i = 0; i < blueprint.blocks.size(); i++) {
                BlueprintBlock block = blueprint.blocks.get(i);
                if (block == null || block.spec == null) {
                    report.errors.add("方块数据损坏，索引 " + i);
                    break;
                }
                if (!"minecraft:air".equals(block.spec.getObjectId().toString()) && block.spec.getBlock() == Blocks.air) {
                    report.errors.add("无法解析方块: " + block.spec.getObjectId());
                    if (report.errors.size() >= 20) break;
                }
            }
        }
        return report;
    }

    public boolean canUse() {
        return errors.isEmpty();
    }

    private static String installedVersion(String modId) {
        ModContainer mod = Loader.instance().getIndexedModList().get(modId);
        return mod == null ? "missing" : mod.getVersion();
    }
}

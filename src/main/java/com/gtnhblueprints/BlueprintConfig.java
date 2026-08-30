package com.gtnhblueprints;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class BlueprintConfig {

    public static int maxBlocks = 250_000;
    public static int maxSpan = 512;
    public static int maxTransferBytes = 8 * 1024 * 1024;
    public static int maxPreviewBlocks = 50_000;
    public static int previewUpdateIntervalMs = 150;
    public static int hostedMachineControllerId = 32600;
    public static int hostedMachineScanIntervalTicks = 100;
    public static int hostedMachineRecipeCheckIntervalTicks = 5;

    private BlueprintConfig() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        maxBlocks = config.getInt("maxBlocks", "limits", maxBlocks, 1, 1_000_000, "Maximum blocks in one blueprint.");
        maxSpan = config.getInt("maxSpan", "limits", maxSpan, 1, 4096, "Maximum size on any blueprint axis.");
        maxTransferBytes = config.getInt(
            "maxTransferBytes",
            "limits",
            maxTransferBytes,
            64 * 1024,
            32 * 1024 * 1024,
            "Maximum compressed .gtbp upload/download size.");
        maxPreviewBlocks = config.getInt(
            "maxPreviewBlocks",
            "limits",
            maxPreviewBlocks,
            100,
            maxBlocks,
            "Maximum non-air blocks shown in the client blueprint projection.");
        previewUpdateIntervalMs = config.getInt(
            "previewUpdateIntervalMs",
            "rendering",
            previewUpdateIntervalMs,
            50,
            1000,
            "Minimum delay between expensive projection rebuilds while moving or stretching a blueprint.");
        hostedMachineControllerId = config.getInt(
            "hostedMachineControllerId",
            "machines",
            hostedMachineControllerId,
            1,
            32766,
            "GregTech MetaTileEntity ID used by the Multiblock Machine Host controller. Changing this in an existing world breaks placed controllers.");
        hostedMachineScanIntervalTicks = config.getInt(
            "hostedMachineScanIntervalTicks",
            "machines",
            hostedMachineScanIntervalTicks,
            20,
            1200,
            "How often the host refreshes its drone-linked machine list. Recipe progress is still updated every tick.");
        hostedMachineRecipeCheckIntervalTicks = config.getInt(
            "hostedMachineRecipeCheckIntervalTicks",
            "machines",
            hostedMachineRecipeCheckIntervalTicks,
            1,
            100,
            "Ticks between round-robin recipe checks. One idle remote is checked each interval; increase this value to reduce idle TPS cost.");
        if (config.hasChanged()) config.save();
    }
}

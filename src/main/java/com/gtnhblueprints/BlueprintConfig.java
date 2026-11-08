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
    public static int quantizedMEInputBusId = 32601;
    public static int quantizedMEInputHatchId = 32602;
    public static int meInputAssemblyId = 32603;
    public static int meOutputAssemblyId = 32604;
    public static int wirelessPatternInputId = 32605;
    public static int wirelessOutputBusId = 32606;
    public static int wirelessOutputHatchId = 32607;
    public static int wirelessOutputAssemblyId = 32608;
    public static int wirelessInputAssemblyId = 32609;
    public static int hostedMachineScanIntervalTicks = 100;
    public static int hostedMachineRecipeCheckIntervalTicks = 5;
    public static boolean hostedMachineEffects = true;

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
        quantizedMEInputBusId = config.getInt("quantizedMEInputBusId", "machines", quantizedMEInputBusId, 1, 32766, "MetaTileEntity ID for the quantified ME item input bus.");
        quantizedMEInputHatchId = config.getInt("quantizedMEInputHatchId", "machines", quantizedMEInputHatchId, 1, 32766, "MetaTileEntity ID for the quantified ME fluid input hatch.");
        meInputAssemblyId = config.getInt("meInputAssemblyId", "machines", meInputAssemblyId, 1, 32766, "MetaTileEntity ID for the manually marked combined ME input assembly.");
        meOutputAssemblyId = config.getInt("meOutputAssemblyId", "machines", meOutputAssemblyId, 1, 32766, "MetaTileEntity ID for the combined ME output assembly.");
        wirelessPatternInputId = config.getInt("wirelessPatternInputId", "machines", wirelessPatternInputId, 1, 32766, "Wireless ME pattern input assembly ID.");
        wirelessOutputBusId = config.getInt("wirelessOutputBusId", "machines", wirelessOutputBusId, 1, 32766, "Wireless ME item output bus ID.");
        wirelessOutputHatchId = config.getInt("wirelessOutputHatchId", "machines", wirelessOutputHatchId, 1, 32766, "Wireless ME fluid output hatch ID.");
        wirelessOutputAssemblyId = config.getInt("wirelessOutputAssemblyId", "machines", wirelessOutputAssemblyId, 1, 32766, "Wireless ME combined output assembly ID.");
        wirelessInputAssemblyId = config.getInt("wirelessInputAssemblyId", "machines", wirelessInputAssemblyId, 1, 32766, "Wireless ME combined input assembly ID.");
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
        HostedMilestoneConfig.load(config);
        hostedMachineEffects = config.getBoolean("hostedMachineEffects", "machines", true,
            "Default for host visual effects. Screwdriver-right-click each host to toggle; existing saved choices are preserved.");
        if (config.hasChanged()) config.save();
    }
}

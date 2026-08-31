package com.gtnhblueprints.registry;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnhblueprints.block.BlockBlueprintConfigurator;
import com.gtnhblueprints.block.BlockHostedMachineCasing;
import com.gtnhblueprints.item.ItemBlockHostedMachineCasing;
import com.gtnhblueprints.tile.TileBlueprintConfigurator;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.casing.Casings;
import gregtech.api.enums.ItemList;

public final class ModBlocks {

    public static final int CONFIGURATOR_GUI_ID = 1;
    public static final BlockBlueprintConfigurator BLUEPRINT_CONFIGURATOR = new BlockBlueprintConfigurator();
    public static final BlockHostedMachineCasing HOSTED_MACHINE_CASING = new BlockHostedMachineCasing();

    private ModBlocks() {}

    public static void preInit() {
        GameRegistry.registerBlock(BLUEPRINT_CONFIGURATOR, "blueprint_configurator");
        GameRegistry.registerBlock(
            HOSTED_MACHINE_CASING,
            ItemBlockHostedMachineCasing.class,
            "hosted_machine_casing");
        GameRegistry.registerTileEntity(TileBlueprintConfigurator.class, "matterblueprints:blueprint_configurator");
    }

    public static void init() {
        GameRegistry.addRecipe(
            new ItemStack(BLUEPRINT_CONFIGURATOR),
            "IGI",
            "RCR",
            "IGI",
            'I',
            Items.iron_ingot,
            'G',
            Blocks.glass,
            'R',
            Items.redstone,
            'C',
            Blocks.crafting_table);
        GameRegistry.addShapelessRecipe(
            new ItemStack(HOSTED_MACHINE_CASING, 1, BlockHostedMachineCasing.CASING),
            Casings.ZPMMachineCasing.toStack(1),
            ItemList.Circuit_Master.get(1));
        GameRegistry.addShapelessRecipe(
            new ItemStack(HOSTED_MACHINE_CASING, 1, BlockHostedMachineCasing.FLOW_LIGHT),
            new ItemStack(HOSTED_MACHINE_CASING, 1, BlockHostedMachineCasing.CASING),
            Items.redstone,
            Items.glowstone_dust,
            new ItemStack(Items.dye, 1, 4));
        GameRegistry.addRecipe(
            new ItemStack(HOSTED_MACHINE_CASING, 1, BlockHostedMachineCasing.RECEIVER),
            "SDS",
            "LCL",
            "SFS",
            'S',
            ItemList.Sensor_ZPM.get(1),
            'D',
            ItemList.Tool_DataOrb.get(1),
            'L',
            new ItemStack(HOSTED_MACHINE_CASING, 1, BlockHostedMachineCasing.FLOW_LIGHT),
            'C',
            new ItemStack(HOSTED_MACHINE_CASING, 1, BlockHostedMachineCasing.CASING),
            'F',
            ItemList.Field_Generator_ZPM.get(1));
    }
}

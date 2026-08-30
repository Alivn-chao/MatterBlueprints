package com.gtnhblueprints.registry;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnhblueprints.block.BlockBlueprintConfigurator;
import com.gtnhblueprints.tile.TileBlueprintConfigurator;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModBlocks {

    public static final int CONFIGURATOR_GUI_ID = 1;
    public static final BlockBlueprintConfigurator BLUEPRINT_CONFIGURATOR = new BlockBlueprintConfigurator();

    private ModBlocks() {}

    public static void preInit() {
        GameRegistry.registerBlock(BLUEPRINT_CONFIGURATOR, "blueprint_configurator");
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
    }
}

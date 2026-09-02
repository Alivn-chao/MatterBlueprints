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
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;

public final class ModBlocks {

    public static final int CONFIGURATOR_GUI_ID = 1;
    public static final BlockBlueprintConfigurator BLUEPRINT_CONFIGURATOR = new BlockBlueprintConfigurator();
    public static final BlockHostedMachineCasing HOSTED_MACHINE_CASING = new BlockHostedMachineCasing();
    public static int HOSTED_HATCH_CASING_TEXTURE_ID = Casings.ZPMMachineCasing.textureId;

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
        registerHostedHatchCasingTexture();
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
        GTModHandler.addShapelessCraftingRecipe(
            new ItemStack(HOSTED_MACHINE_CASING, 1, BlockHostedMachineCasing.CASING),
            new Object[] { Casings.ZPMMachineCasing.toStack(1), OrePrefixes.circuit.get(Materials.LuV) });
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
        GameRegistry.addRecipe(
            new ItemStack(HOSTED_MACHINE_CASING, 4, BlockHostedMachineCasing.COOLING_FAN),
            "RMR",
            "MCM",
            "RMR",
            'R',
            Items.redstone,
            'M',
            ItemList.Electric_Motor_ZPM.get(1),
            'C',
            new ItemStack(HOSTED_MACHINE_CASING, 1, BlockHostedMachineCasing.CASING));
    }

    private static void registerHostedHatchCasingTexture() {
        ITexture texture = TextureFactory.of(HOSTED_MACHINE_CASING, BlockHostedMachineCasing.CASING);
        ITexture[][] pages = Textures.BlockIcons.casingTexturePages;
        for (int page = pages.length - 1; page >= 0; page--) {
            ITexture[] entries = pages[page];
            if (entries == null) continue;
            for (int index = entries.length - 1; index >= 0; index--) {
                if (entries[index] != null) continue;
                HOSTED_HATCH_CASING_TEXTURE_ID = page << 7 | index;
                Textures.BlockIcons.setCasingTextureForId(HOSTED_HATCH_CASING_TEXTURE_ID, texture);
                return;
            }
        }
        throw new IllegalStateException("No free GregTech casing texture slot for hosted machine hatches");
    }
}

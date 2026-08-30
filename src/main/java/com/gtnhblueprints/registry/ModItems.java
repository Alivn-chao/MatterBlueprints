package com.gtnhblueprints.registry;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.gtnhblueprints.item.ItemHostedMachineBinder;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    public static final ItemHostedMachineBinder HOSTED_MACHINE_BINDER = new ItemHostedMachineBinder();

    private ModItems() {}

    public static void preInit() {
        GameRegistry.registerItem(HOSTED_MACHINE_BINDER, "hosted_machine_binder");
        MinecraftForge.EVENT_BUS.register(HOSTED_MACHINE_BINDER);
    }

    public static void init() {
        GameRegistry.addRecipe(
            new ItemStack(HOSTED_MACHINE_BINDER),
            " R ",
            "ICI",
            " S ",
            'R',
            Items.redstone,
            'I',
            Items.iron_ingot,
            'C',
            Items.comparator,
            'S',
            Items.stick);
    }
}

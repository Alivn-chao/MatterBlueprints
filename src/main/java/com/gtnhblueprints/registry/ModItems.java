package com.gtnhblueprints.registry;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.gtnhblueprints.item.ItemHostedMachineBinder;
import com.gtnhblueprints.item.ItemHostedWirelessUpgrade;


import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    public static final ItemHostedMachineBinder HOSTED_MACHINE_BINDER = new ItemHostedMachineBinder();
    public static final ItemHostedWirelessUpgrade HOSTED_WIRELESS_UPGRADE = new ItemHostedWirelessUpgrade();

    private ModItems() {}

    public static void preInit() {
        GameRegistry.registerItem(HOSTED_MACHINE_BINDER, "hosted_machine_binder");
        GameRegistry.registerItem(HOSTED_WIRELESS_UPGRADE, "hosted_wireless_upgrade");
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

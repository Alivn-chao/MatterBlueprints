package com.gtnhblueprints.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

public final class ItemHostedWirelessUpgrade extends Item {

    public ItemHostedWirelessUpgrade() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setUnlocalizedName("matterblueprints.hostedWirelessUpgrade");
        setTextureName("minecraft:ender_eye");
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("matterblueprints.wireless.upgrade_desc"));
        lines.add(StatCollector.translateToLocal("matterblueprints.wireless.owner_only"));
    }
}

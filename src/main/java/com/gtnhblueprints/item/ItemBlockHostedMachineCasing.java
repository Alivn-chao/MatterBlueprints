package com.gtnhblueprints.item;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.gtnhblueprints.block.BlockHostedMachineCasing;

public final class ItemBlockHostedMachineCasing extends ItemBlock {

    private static final String[] NAMES = { "casing", "light", "receiver", "fan" };

    public ItemBlockHostedMachineCasing(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        int metadata = stack.getItemDamage();
        if (metadata < BlockHostedMachineCasing.CASING || metadata > BlockHostedMachineCasing.COOLING_FAN) {
            metadata = BlockHostedMachineCasing.CASING;
        }
        return super.getUnlocalizedName() + "." + NAMES[metadata];
    }
}

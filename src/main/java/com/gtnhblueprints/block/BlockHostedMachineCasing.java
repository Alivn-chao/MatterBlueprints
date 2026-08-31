package com.gtnhblueprints.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class BlockHostedMachineCasing extends Block {

    public static final int CASING = 0;
    public static final int FLOW_LIGHT = 1;
    public static final int RECEIVER = 2;

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;
    @SideOnly(Side.CLIENT)
    private IIcon receiverTopIcon;
    @SideOnly(Side.CLIENT)
    private IIcon receiverSideIcon;

    public BlockHostedMachineCasing() {
        super(Material.iron);
        setBlockName("hostedMachineCasing");
        setCreativeTab(CreativeTabs.tabBlock);
        setHardness(8.0F);
        setResistance(30.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[] {
            register.registerIcon("matterblueprints:host_casing"),
            register.registerIcon("matterblueprints:host_casing_light"),
            register.registerIcon("matterblueprints:host_receiver_top") };
        receiverTopIcon = icons[RECEIVER];
        receiverSideIcon = register.registerIcon("matterblueprints:host_receiver_side");
        blockIcon = icons[CASING];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        int normalizedMetadata = normalizeMetadata(metadata);
        if (normalizedMetadata == RECEIVER) return side == 0 || side == 1 ? receiverTopIcon : receiverSideIcon;
        return icons[normalizedMetadata];
    }

    @Override
    public int damageDropped(int metadata) {
        return normalizeMetadata(metadata);
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        int metadata = normalizeMetadata(world.getBlockMetadata(x, y, z));
        return metadata == FLOW_LIGHT ? 10 : metadata == RECEIVER ? 12 : 0;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, CASING));
        list.add(new ItemStack(item, 1, FLOW_LIGHT));
        list.add(new ItemStack(item, 1, RECEIVER));
    }

    private static int normalizeMetadata(int metadata) {
        return metadata >= CASING && metadata <= RECEIVER ? metadata : CASING;
    }
}

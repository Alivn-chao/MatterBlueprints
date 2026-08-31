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

    private static final int CONNECTED_TEXTURE_COUNT = 16;
    private static final int EDGE_TOP = 1;
    private static final int EDGE_RIGHT = 2;
    private static final int EDGE_BOTTOM = 4;
    private static final int EDGE_LEFT = 8;

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;
    @SideOnly(Side.CLIENT)
    private IIcon[] connectedCasingIcons;
    @SideOnly(Side.CLIENT)
    private IIcon[] connectedLightIcons;
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
        connectedCasingIcons = new IIcon[CONNECTED_TEXTURE_COUNT];
        for (int mask = 0; mask < connectedCasingIcons.length; mask++) {
            connectedCasingIcons[mask] = register.registerIcon("matterblueprints:host_casing_ctm_" + mask);
        }
        connectedLightIcons = new IIcon[CONNECTED_TEXTURE_COUNT];
        for (int mask = 0; mask < connectedLightIcons.length; mask++) {
            connectedLightIcons[mask] = register.registerIcon("matterblueprints:host_casing_light_ctm_" + mask);
        }
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
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int metadata = normalizeMetadata(world.getBlockMetadata(x, y, z));
        if (metadata == CASING) return connectedCasingIcons[getConnectionMask(world, x, y, z, side, CASING)];
        if (metadata == FLOW_LIGHT) {
            return connectedLightIcons[getConnectionMask(world, x, y, z, side, FLOW_LIGHT)];
        }
        return getIcon(side, metadata);
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

    private int getConnectionMask(IBlockAccess world, int x, int y, int z, int side, int metadata) {
        int mask = 0;
        if (side == 0 || side == 1) {
            if (connects(world, x, y, z - 1, metadata)) mask |= EDGE_TOP;
            if (connects(world, x + 1, y, z, metadata)) mask |= EDGE_RIGHT;
            if (connects(world, x, y, z + 1, metadata)) mask |= EDGE_BOTTOM;
            if (connects(world, x - 1, y, z, metadata)) mask |= EDGE_LEFT;
        } else if (side == 2 || side == 3) {
            if (connects(world, x, y + 1, z, metadata)) mask |= EDGE_TOP;
            if (connects(world, x + 1, y, z, metadata)) mask |= EDGE_RIGHT;
            if (connects(world, x, y - 1, z, metadata)) mask |= EDGE_BOTTOM;
            if (connects(world, x - 1, y, z, metadata)) mask |= EDGE_LEFT;
        } else {
            if (connects(world, x, y + 1, z, metadata)) mask |= EDGE_TOP;
            if (connects(world, x, y, z + 1, metadata)) mask |= EDGE_RIGHT;
            if (connects(world, x, y - 1, z, metadata)) mask |= EDGE_BOTTOM;
            if (connects(world, x, y, z - 1, metadata)) mask |= EDGE_LEFT;
        }
        return mask;
    }

    private boolean connects(IBlockAccess world, int x, int y, int z, int metadata) {
        return world.getBlock(x, y, z) == this && normalizeMetadata(world.getBlockMetadata(x, y, z)) == metadata;
    }
}

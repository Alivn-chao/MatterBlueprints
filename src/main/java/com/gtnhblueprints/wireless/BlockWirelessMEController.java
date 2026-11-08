package com.gtnhblueprints.wireless;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.creativetab.CreativeTabs;
import appeng.block.networking.BlockController;
import com.cleanroommc.modularui.factory.GuiFactories;

public final class BlockWirelessMEController extends BlockController {
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private net.minecraft.util.IIcon wirelessBadge;
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister icons) {
        super.registerBlockIcons(icons);
        wirelessBadge = icons.registerIcon("appliedenergistics2:BlockWirelessOn");
    }
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.IIcon wirelessBadge() { return wirelessBadge; }
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    protected appeng.client.render.blocks.RenderBlockController getRenderer() {
        return new com.gtnhblueprints.client.RenderWirelessMEController();
    }
    public BlockWirelessMEController() {
        setTileEntity(TileWirelessMEController.class);
        setBlockName("matterblueprints.wireless_me_controller");
        setBlockTextureName("appliedenergistics2:BlockController");
        setCreativeTab(CreativeTabs.tabRedstone);
    }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
        int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) return false;
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileWirelessMEController)
            GuiFactories.tileEntity().open(player, (TileWirelessMEController) world.getTileEntity(x, y, z));
        return true;
    }
    @Override public void breakBlock(World world, int x, int y, int z, Block old, int meta) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileWirelessMEController)
            ((TileWirelessMEController) world.getTileEntity(x, y, z)).wireless().removed();
        super.breakBlock(world, x, y, z, old, meta);
    }
}

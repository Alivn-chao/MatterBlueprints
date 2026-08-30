package com.gtnhblueprints.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.gtnhblueprints.MatterBlueprints;
import com.gtnhblueprints.registry.ModBlocks;
import com.gtnhblueprints.tile.TileBlueprintConfigurator;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class BlockBlueprintConfigurator extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon frontIcon;
    @SideOnly(Side.CLIENT)
    private IIcon topIcon;
    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;

    public BlockBlueprintConfigurator() {
        super(Material.iron);
        setBlockName("blueprintConfigurator");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabTools);
        setHardness(4.0F);
        setResistance(12.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileBlueprintConfigurator();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        frontIcon = register.registerIcon("matterblueprints:blueprint_configurator_front");
        topIcon = register.registerIcon("matterblueprints:blueprint_configurator_top");
        sideIcon = register.registerIcon("matterblueprints:blueprint_configurator_side");
        blockIcon = sideIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        if (side == 1) return topIcon;
        int front = metadata >= 2 && metadata <= 5 ? metadata : 3;
        return side == front ? frontIcon : sideIcon;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int direction = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int front = direction == 0 ? 2 : direction == 1 ? 5 : direction == 2 ? 3 : 4;
        world.setBlockMetadataWithNotify(x, y, z, front, 2);
    }

    @Override
    public boolean onBlockActivated(
        World world,
        int x,
        int y,
        int z,
        EntityPlayer player,
        int side,
        float hitX,
        float hitY,
        float hitZ
    ) {
        if (!world.isRemote) player.openGui(MatterBlueprints.instance, ModBlocks.CONFIGURATOR_GUI_ID, world, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int metadata) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!world.isRemote && tile instanceof TileBlueprintConfigurator) {
            TileBlueprintConfigurator configurator = (TileBlueprintConfigurator) tile;
            ItemStack stack = configurator.getStackInSlotOnClosing(0);
            if (stack != null) {
                EntityItem dropped = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack);
                dropped.motionX = world.rand.nextGaussian() * 0.05;
                dropped.motionY = world.rand.nextGaussian() * 0.05 + 0.2;
                dropped.motionZ = world.rand.nextGaussian() * 0.05;
                world.spawnEntityInWorld(dropped);
            }
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }
}

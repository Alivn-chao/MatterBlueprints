package com.gtnhblueprints.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.gtnhblueprints.machine.MTEHostedMachineController;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public final class ItemHostedMachineBinder extends Item {

    private static final String HOST_DIMENSION = "hostDimension";
    private static final String HOST_X = "hostX";
    private static final String HOST_Y = "hostY";
    private static final String HOST_Z = "hostZ";

    public ItemHostedMachineBinder() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
        setUnlocalizedName("matterblueprints.hostedMachineBinder");
        setTextureName("minecraft:comparator");
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK || !event.entityPlayer.isSneaking()) return;
        ItemStack held = event.entityPlayer.getHeldItem();
        if (held == null || held.getItem() != this) return;
        MTEHostedMachineController host = getHostController(event.entityPlayer.worldObj, event.x, event.y, event.z);
        if (host == null) return;
        if (event.entityPlayer.worldObj.isRemote) return;
        event.setCanceled(true);

        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        NBTTagCompound tag = getOrCreateTag(held);
        tag.setInteger(HOST_DIMENSION, tile.getWorld().provider.dimensionId);
        tag.setInteger(HOST_X, tile.getXCoord());
        tag.setInteger(HOST_Y, tile.getYCoord());
        tag.setInteger(HOST_Z, tile.getZCoord());
        event.entityPlayer.addChatMessage(
            new ChatComponentTranslation(
                "matterblueprints.binder.host_set",
                tile.getXCoord(),
                tile.getYCoord(),
                tile.getZCoord(),
                tile.getWorld().provider.dimensionId));
    }

    @Override
    public boolean onItemUseFirst(
        ItemStack stack,
        EntityPlayer player,
        World world,
        int x,
        int y,
        int z,
        int side,
        float hitX,
        float hitY,
        float hitZ
    ) {
        IMetaTileEntity target = getMetaTileEntity(world, x, y, z);
        if (!(target instanceof MTEMultiBlockBase)) return false;
        // The client must return false so vanilla sends the use packet. The server handles it here before the GT block
        // can open its GUI, and returns true to consume the interaction.
        if (world.isRemote) return false;
        if (target instanceof MTEHostedMachineController) {
            player.addChatMessage(new ChatComponentTranslation("matterblueprints.binder.use_shift_left"));
            return true;
        }

        MTEHostedMachineController host = resolveStoredHost(stack);
        if (host == null) {
            player.addChatMessage(new ChatComponentTranslation("matterblueprints.binder.host_missing"));
            return true;
        }
        MTEHostedMachineController.BindingResult result = host.toggleHostedBinding((MTEMultiBlockBase) target);
        player.addChatMessage(new ChatComponentTranslation(result.translationKey, result.boundCount));
        return true;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(HOST_DIMENSION)) {
            tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("matterblueprints.binder.tooltip.unset"));
            return;
        }
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted(
                "matterblueprints.binder.tooltip.host",
                tag.getInteger(HOST_X),
                tag.getInteger(HOST_Y),
                tag.getInteger(HOST_Z),
                tag.getInteger(HOST_DIMENSION)));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("matterblueprints.binder.tooltip.usage"));
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    private static MTEHostedMachineController resolveStoredHost(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(HOST_DIMENSION)) return null;
        World world = DimensionManager.getWorld(tag.getInteger(HOST_DIMENSION));
        if (world == null) return null;
        return getHostController(world, tag.getInteger(HOST_X), tag.getInteger(HOST_Y), tag.getInteger(HOST_Z));
    }

    private static MTEHostedMachineController getHostController(World world, int x, int y, int z) {
        IMetaTileEntity metaTileEntity = getMetaTileEntity(world, x, y, z);
        return metaTileEntity instanceof MTEHostedMachineController ? (MTEHostedMachineController) metaTileEntity : null;
    }

    private static IMetaTileEntity getMetaTileEntity(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity)) return null;
        return ((IGregTechTileEntity) tile).getMetaTileEntity();
    }
}

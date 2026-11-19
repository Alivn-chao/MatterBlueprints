package com.gtnhblueprints.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;

import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class WirelessOutputCopy {
    private WirelessOutputCopy() {}

    public interface Trait {
        String matterblueprints$getWirelessOutputChannel();
        void matterblueprints$setWirelessOutputChannel(String channel);
    }

    public static String capture(IGregTechTileEntity tile) {
        if (tile.getWorld() == null || tile.getWorld().isRemote) return null;
        if (!(tile.getMetaTileEntity() instanceof MTEWirelessOutputAssembly)) return null;
        String channel = ((MTEWirelessOutputAssembly) tile.getMetaTileEntity()).wireless().channelId();
        return channel.isEmpty() ? null : channel;
    }

    public static void apply(String channel, IBlockApplyContext context) {
        if (channel == null || channel.isEmpty() || context.getWorld().isRemote) return;
        EntityPlayer player = context.getRealPlayer();
        if (player == null) return;
        TileEntity tile = context.getTileEntity();
        WirelessMEEndpoint endpoint = WirelessMEService.endpoint(tile);
        if (!(endpoint instanceof MTEWirelessOutputAssembly)) return;
        try {
            endpoint.wireless().join(player, channel);
        } catch (IllegalArgumentException failure) {
            context.warn(new ChatComponentTranslation(failure.getMessage()));
        }
    }
}

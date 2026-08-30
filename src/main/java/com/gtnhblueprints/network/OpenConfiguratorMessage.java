package com.gtnhblueprints.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnhblueprints.MatterBlueprints;
import com.gtnhblueprints.registry.ModBlocks;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Opens the configurator without allowing the held Matter Manipulator to consume the block click first. */
public final class OpenConfiguratorMessage implements IMessage {

    public int x;
    public int y;
    public int z;

    public OpenConfiguratorMessage() {}

    public OpenConfiguratorMessage(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
    }

    public static final class Handler implements IMessageHandler<OpenConfiguratorMessage, IMessage> {

        @Override
        public IMessage onMessage(final OpenConfiguratorMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            BlueprintNetwork.enqueueServer(() -> open(message, player));
            return null;
        }

        private static void open(OpenConfiguratorMessage message, EntityPlayerMP player) {
            if (message.y < 0 || message.y > 255) return;
            if (player.getDistanceSq(message.x + 0.5, message.y + 0.5, message.z + 0.5) > 64.0) return;
            if (player.worldObj.getBlock(message.x, message.y, message.z) != ModBlocks.BLUEPRINT_CONFIGURATOR) return;
            player.openGui(
                MatterBlueprints.instance,
                ModBlocks.CONFIGURATOR_GUI_ID,
                player.worldObj,
                message.x,
                message.y,
                message.z);
        }
    }
}

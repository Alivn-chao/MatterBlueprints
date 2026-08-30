package com.gtnhblueprints.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnhblueprints.MatterBlueprints;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public final class BlueprintUploadChunkMessage extends BlueprintChunkMessage {

    public BlueprintUploadChunkMessage() {}

    BlueprintUploadChunkMessage(
        int transferId,
        String name,
        int totalSize,
        int chunkCount,
        int chunkIndex,
        byte[] chunk
    ) {
        super(transferId, (byte) 0, name, totalSize, chunkCount, chunkIndex, chunk);
    }

    public static final class Handler implements IMessageHandler<BlueprintUploadChunkMessage, IMessage> {

        @Override
        public IMessage onMessage(final BlueprintUploadChunkMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            try {
                final byte[] completed = BlueprintTransferAssembler.accept(
                    player.getUniqueID().toString(),
                    message);
                if (completed != null) {
                    BlueprintNetwork.enqueueServer(new Runnable() {

                        @Override
                        public void run() {
                            BlueprintUploadMessage.Handler.accept(message.name, completed, player);
                        }
                    });
                }
            } catch (RuntimeException exception) {
                MatterBlueprints.LOG.warn("Rejected chunked blueprint upload from " + player.getCommandSenderName(), exception);
            }
            return null;
        }
    }
}

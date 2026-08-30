package com.gtnhblueprints.network;

import com.gtnhblueprints.MatterBlueprints;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public final class BlueprintClientChunkMessage extends BlueprintChunkMessage {

    static final byte ACTIVATE = 1;
    static final byte DOWNLOAD = 2;

    public BlueprintClientChunkMessage() {}

    BlueprintClientChunkMessage(
        int transferId,
        byte kind,
        String name,
        int totalSize,
        int chunkCount,
        int chunkIndex,
        byte[] chunk
    ) {
        super(transferId, kind, name, totalSize, chunkCount, chunkIndex, chunk);
    }

    public static final class Handler implements IMessageHandler<BlueprintClientChunkMessage, IMessage> {

        @Override
        public IMessage onMessage(BlueprintClientChunkMessage message, MessageContext context) {
            try {
                byte[] completed = BlueprintTransferAssembler.accept("client", message);
                if (completed == null) return null;
                if (message.kind == ACTIVATE) {
                    MatterBlueprints.proxy.receiveActivatedBlueprint(message.name, completed);
                } else if (message.kind == DOWNLOAD) {
                    MatterBlueprints.proxy.receiveDownloadedBlueprint(message.name, completed);
                } else {
                    throw new IllegalArgumentException("Unknown client blueprint transfer kind");
                }
            } catch (RuntimeException exception) {
                MatterBlueprints.LOG.warn("Rejected chunked blueprint transfer from server", exception);
            }
            return null;
        }
    }
}

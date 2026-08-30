package com.gtnhblueprints.network;

import com.gtnhblueprints.MatterBlueprints;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public final class BlueprintDownloadMessage extends BlueprintTransferMessage {

    public BlueprintDownloadMessage() {}

    public BlueprintDownloadMessage(String name, byte[] bytes) {
        super(name, bytes);
    }

    public static final class Handler implements IMessageHandler<BlueprintDownloadMessage, IMessage> {

        @Override
        public IMessage onMessage(BlueprintDownloadMessage message, MessageContext context) {
            MatterBlueprints.proxy.receiveDownloadedBlueprint(message.name, message.bytes);
            return null;
        }
    }
}

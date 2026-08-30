package com.gtnhblueprints.network;

import com.gtnhblueprints.MatterBlueprints;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public final class BlueprintActivateMessage extends BlueprintTransferMessage {

    public BlueprintActivateMessage() {}

    public BlueprintActivateMessage(String name, byte[] bytes) {
        super(name, bytes);
    }

    public static final class Handler implements IMessageHandler<BlueprintActivateMessage, IMessage> {

        @Override
        public IMessage onMessage(BlueprintActivateMessage message, MessageContext context) {
            MatterBlueprints.proxy.receiveActivatedBlueprint(message.name, message.bytes);
            return null;
        }
    }
}

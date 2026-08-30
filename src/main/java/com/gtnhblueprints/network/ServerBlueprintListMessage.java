package com.gtnhblueprints.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gtnhblueprints.MatterBlueprints;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class ServerBlueprintListMessage implements IMessage {

    private static final int MAX_NAMES = 4096;
    public List<String> names = Collections.emptyList();

    public ServerBlueprintListMessage() {}

    public ServerBlueprintListMessage(List<String> names) {
        this.names = new ArrayList<>(names.subList(0, Math.min(names.size(), MAX_NAMES)));
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int count = buffer.readUnsignedShort();
        if (count > MAX_NAMES) throw new IllegalArgumentException("服务器蓝图列表过长");
        List<String> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) decoded.add(ByteBufUtils.readUTF8String(buffer));
        names = decoded;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeShort(names.size());
        for (String name : names) ByteBufUtils.writeUTF8String(buffer, name);
    }

    public static final class Handler implements IMessageHandler<ServerBlueprintListMessage, IMessage> {

        @Override
        public IMessage onMessage(ServerBlueprintListMessage message, MessageContext context) {
            MatterBlueprints.proxy.receiveServerBlueprintList(message.names);
            return null;
        }
    }
}

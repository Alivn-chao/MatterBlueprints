package com.gtnhblueprints.network;

import com.gtnhblueprints.BlueprintConfig;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public abstract class BlueprintTransferMessage implements IMessage {

    public String name;
    public byte[] bytes;

    protected BlueprintTransferMessage() {}

    protected BlueprintTransferMessage(String name, byte[] bytes) {
        this.name = name;
        this.bytes = bytes;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        name = ByteBufUtils.readUTF8String(buffer);
        int size = buffer.readInt();
        if (size < 1 || size > BlueprintConfig.maxTransferBytes || size > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid blueprint payload size: " + size);
        }
        bytes = new byte[size];
        buffer.readBytes(bytes);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (bytes == null || bytes.length < 1 || bytes.length > BlueprintConfig.maxTransferBytes) {
            throw new IllegalArgumentException("Invalid blueprint payload");
        }
        ByteBufUtils.writeUTF8String(buffer, name);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }
}

package com.gtnhblueprints.network;

import com.gtnhblueprints.BlueprintConfig;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** One transport-safe part of a compressed blueprint. */
abstract class BlueprintChunkMessage implements IMessage {

    static final int CHUNK_BYTES = 24 * 1024;

    int transferId;
    byte kind;
    String name;
    int totalSize;
    int chunkCount;
    int chunkIndex;
    byte[] chunk;

    BlueprintChunkMessage() {}

    BlueprintChunkMessage(
        int transferId,
        byte kind,
        String name,
        int totalSize,
        int chunkCount,
        int chunkIndex,
        byte[] chunk
    ) {
        this.transferId = transferId;
        this.kind = kind;
        this.name = name;
        this.totalSize = totalSize;
        this.chunkCount = chunkCount;
        this.chunkIndex = chunkIndex;
        this.chunk = chunk;
    }

    @Override
    public final void fromBytes(ByteBuf buffer) {
        transferId = buffer.readInt();
        kind = buffer.readByte();
        name = ByteBufUtils.readUTF8String(buffer);
        totalSize = buffer.readInt();
        chunkCount = buffer.readInt();
        chunkIndex = buffer.readInt();
        int chunkSize = buffer.readUnsignedShort();
        if (totalSize < 1 || totalSize > BlueprintConfig.maxTransferBytes || chunkSize < 1
            || chunkSize > CHUNK_BYTES
            || chunkSize > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid blueprint chunk payload");
        }
        chunk = new byte[chunkSize];
        buffer.readBytes(chunk);
    }

    @Override
    public final void toBytes(ByteBuf buffer) {
        if (name == null || chunk == null || chunk.length < 1 || chunk.length > CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid blueprint chunk");
        }
        buffer.writeInt(transferId);
        buffer.writeByte(kind);
        ByteBufUtils.writeUTF8String(buffer, name);
        buffer.writeInt(totalSize);
        buffer.writeInt(chunkCount);
        buffer.writeInt(chunkIndex);
        buffer.writeShort(chunk.length);
        buffer.writeBytes(chunk);
    }
}

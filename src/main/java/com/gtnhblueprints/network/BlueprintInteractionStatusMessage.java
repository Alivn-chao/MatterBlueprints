package com.gtnhblueprints.network;

import com.gtnhblueprints.MatterBlueprints;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class BlueprintInteractionStatusMessage implements IMessage {

    public static final byte ORIGIN_CONFIRMED = 1;
    public static final byte BUILD_STARTED = 2;
    public static final byte TRANSFORM_CHANGED = 3;
    public static final byte ERROR = 4;
    public static final byte PREVIEW_CLEARED = 5;

    public byte kind;
    public int originX;
    public int originY;
    public int originZ;
    public int quarterTurns;
    public boolean mirrorX;
    public boolean mirrorZ;
    public String text = "";

    public BlueprintInteractionStatusMessage() {}

    public BlueprintInteractionStatusMessage(byte kind, com.gtnhblueprints.service.BoundBlueprint bound, String text) {
        this.kind = kind;
        if (bound != null) {
            originX = bound.originX;
            originY = bound.originY;
            originZ = bound.originZ;
            quarterTurns = bound.quarterTurns;
            mirrorX = bound.mirrorX;
            mirrorZ = bound.mirrorZ;
        }
        this.text = text == null ? "" : text;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        kind = buffer.readByte();
        originX = buffer.readInt();
        originY = buffer.readInt();
        originZ = buffer.readInt();
        quarterTurns = buffer.readByte();
        mirrorX = buffer.readBoolean();
        mirrorZ = buffer.readBoolean();
        text = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(kind);
        buffer.writeInt(originX);
        buffer.writeInt(originY);
        buffer.writeInt(originZ);
        buffer.writeByte(quarterTurns);
        buffer.writeBoolean(mirrorX);
        buffer.writeBoolean(mirrorZ);
        ByteBufUtils.writeUTF8String(buffer, text);
    }

    public static final class Handler implements IMessageHandler<BlueprintInteractionStatusMessage, IMessage> {

        @Override
        public IMessage onMessage(BlueprintInteractionStatusMessage message, MessageContext context) {
            MatterBlueprints.proxy.receiveInteractionStatus(
                message.kind,
                message.originX,
                message.originY,
                message.originZ,
                message.quarterTurns,
                message.mirrorX,
                message.mirrorZ,
                message.text);
            return null;
        }
    }
}

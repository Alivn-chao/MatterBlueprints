package com.gtnhblueprints.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnhblueprints.service.BlueprintInteractionService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class BlueprintRightClickMessage implements IMessage {

    public static final byte INTERACT = 0;
    public static final byte MARK_ARRAY = 1;
    public static final byte CANCEL = 2;
    public byte kind;

    public BlueprintRightClickMessage() {}

    public BlueprintRightClickMessage(byte kind) {
        this.kind = kind;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        kind = buffer.readByte();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(kind);
    }

    public static final class Handler implements IMessageHandler<BlueprintRightClickMessage, IMessage> {

        @Override
        public IMessage onMessage(BlueprintRightClickMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            BlueprintNetwork.enqueueServer(new Runnable() {

                @Override
                public void run() {
                    if (message.kind == CANCEL) {
                        BlueprintInteractionService.cancel(player);
                    } else if (message.kind == MARK_ARRAY) {
                        try {
                            BlueprintInteractionService.markArray(player);
                        } catch (RuntimeException exception) {
                            player.addChatMessage(
                                new net.minecraft.util.ChatComponentText("§c[GTBP] " + exception.getMessage()));
                        }
                    } else {
                        BlueprintInteractionService.rightClick(player);
                    }
                }
            });
            return null;
        }
    }
}

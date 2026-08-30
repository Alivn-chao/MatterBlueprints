package com.gtnhblueprints.network;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.gtnhblueprints.MatterBlueprints;
import com.gtnhblueprints.io.BlueprintIO;
import com.gtnhblueprints.service.BlueprintLibrary;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class ConfiguratorRequestMessage implements IMessage {

    public static final byte LIST = 0;
    public static final byte DOWNLOAD = 1;

    public byte action;
    public String name = "";

    public ConfiguratorRequestMessage() {}

    public ConfiguratorRequestMessage(byte action, String name) {
        this.action = action;
        this.name = name == null ? "" : name;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        action = buffer.readByte();
        name = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(action);
        ByteBufUtils.writeUTF8String(buffer, name);
    }

    public static final class Handler implements IMessageHandler<ConfiguratorRequestMessage, IMessage> {

        @Override
        public IMessage onMessage(final ConfiguratorRequestMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            BlueprintNetwork.enqueueServer(() -> handle(message, player));
            return null;
        }

        private static void handle(ConfiguratorRequestMessage message, EntityPlayerMP player) {
            try {
                if (message.action == LIST) {
                    BlueprintNetwork.serverBlueprintList(player, BlueprintLibrary.list());
                    return;
                }
                if (message.action == DOWNLOAD) {
                    String safeName = BlueprintIO.safeName(message.name);
                    byte[] bytes = BlueprintLibrary.readBytes(safeName);
                    BlueprintNetwork.download(player, safeName, bytes);
                    player.addChatMessage(new ChatComponentText("§a[GTBP] 配置机正在下载 " + safeName));
                    return;
                }
                throw new IOException("未知配置机请求");
            } catch (IOException | RuntimeException exception) {
                MatterBlueprints.LOG.warn("Rejected blueprint configurator request from " + player.getCommandSenderName(), exception);
                player.addChatMessage(new ChatComponentText("§c[GTBP] 配置机请求失败: " + exception.getMessage()));
            }
        }
    }
}

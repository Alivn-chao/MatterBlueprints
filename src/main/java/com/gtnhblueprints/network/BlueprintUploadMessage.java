package com.gtnhblueprints.network;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.gtnhblueprints.MatterBlueprints;
import com.gtnhblueprints.io.BlueprintIO;
import com.gtnhblueprints.model.Blueprint;
import com.gtnhblueprints.model.CompatibilityReport;
import com.gtnhblueprints.service.BlueprintLibrary;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public final class BlueprintUploadMessage extends BlueprintTransferMessage {

    public BlueprintUploadMessage() {}

    public BlueprintUploadMessage(String name, byte[] bytes) {
        super(name, bytes);
    }

    public static final class Handler implements IMessageHandler<BlueprintUploadMessage, IMessage> {

        @Override
        public IMessage onMessage(final BlueprintUploadMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            BlueprintNetwork.enqueueServer(new Runnable() {

                @Override
                public void run() {
                    accept(message, player);
                }
            });
            return null;
        }

        private static void accept(BlueprintUploadMessage message, EntityPlayerMP player) {
            accept(message.name, message.bytes, player);
        }

        static void accept(String name, byte[] bytes, EntityPlayerMP player) {
            try {
                String safeName = BlueprintIO.safeName(name);
                Blueprint blueprint = BlueprintIO.decode(bytes);
                blueprint.name = safeName;
                CompatibilityReport report = BlueprintLibrary.bind(player, blueprint, bytes);
                BlueprintLibrary.sendReport(player, report);
                if (report.canUse()) {
                    player.addChatMessage(new ChatComponentText("§a[GTBP] 已从客户端临时上传 " + bytes.length + " 字节"));
                }
            } catch (IOException | RuntimeException exception) {
                MatterBlueprints.LOG.warn("Rejected blueprint upload from " + player.getCommandSenderName(), exception);
                player.addChatMessage(new ChatComponentText("§c[GTBP] 导入失败: " + exception.getMessage()));
            }
        }
    }
}

package com.gtnhblueprints.client;

import java.awt.Desktop;
import java.io.IOException;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.gtnhblueprints.io.BlueprintIO;
import com.gtnhblueprints.network.BlueprintNetwork;
import com.gtnhblueprints.service.BlueprintLibrary;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class LocalBlueprintCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtbplocal";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtbplocal <list|import <名称>|folder>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        try {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "list":
                    list(sender);
                    break;
                case "import":
                    if (args.length < 2) throw new IOException("用法: /gtbplocal import <名称>");
                    String name = BlueprintIO.safeName(args[1]);
                    byte[] bytes = BlueprintIO.read(BlueprintLibrary.directory(), name);
                    BlueprintNetwork.upload(name, bytes);
                    sender.addChatMessage(new ChatComponentText("§a[GTBP] 正在上传 " + name + "（" + bytes.length + " 字节）"));
                    break;
                case "folder":
                    if (!Desktop.isDesktopSupported()) throw new IOException("当前环境不支持打开文件夹");
                    Desktop.getDesktop().open(BlueprintLibrary.directory());
                    break;
                default:
                    throw new IOException(getCommandUsage(sender));
            }
        } catch (IOException | RuntimeException exception) {
            sender.addChatMessage(new ChatComponentText("§c[GTBP] " + exception.getMessage()));
        }
    }

    private static void list(ICommandSender sender) {
        List<String> names = BlueprintIO.list(BlueprintLibrary.directory());
        sender.addChatMessage(new ChatComponentText("§b[GTBP] 本地蓝图（" + names.size() + "）: " + String.join(", ", names)));
    }
}

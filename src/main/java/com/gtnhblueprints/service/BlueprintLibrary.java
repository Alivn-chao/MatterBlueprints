package com.gtnhblueprints.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.gtnhblueprints.io.BlueprintIO;
import com.gtnhblueprints.model.Blueprint;
import com.gtnhblueprints.model.CompatibilityReport;

public final class BlueprintLibrary {

    private static final Map<UUID, BoundBlueprint> BOUND = new ConcurrentHashMap<>();
    private static File directory;

    private BlueprintLibrary() {}

    public static void initialize(File value) {
        directory = value;
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建蓝图目录: " + directory);
        }
    }

    public static File directory() {
        if (directory == null) throw new IllegalStateException("蓝图库尚未初始化");
        return directory;
    }

    public static byte[] save(Blueprint blueprint) throws IOException {
        byte[] bytes = BlueprintIO.encode(blueprint);
        BlueprintIO.write(directory(), blueprint.name, bytes);
        return bytes;
    }

    public static Blueprint load(String name) throws IOException {
        return BlueprintIO.decode(BlueprintIO.read(directory(), name));
    }

    public static byte[] readBytes(String name) throws IOException {
        return BlueprintIO.read(directory(), name);
    }

    public static List<String> list() {
        return BlueprintIO.list(directory());
    }

    public static CompatibilityReport bind(EntityPlayerMP player, Blueprint blueprint, byte[] bytes) {
        CompatibilityReport report = CompatibilityReport.inspect(blueprint);
        if (report.canUse()) {
            BoundBlueprint bound = new BoundBlueprint(blueprint);
            BOUND.put(player.getUniqueID(), bound);
            try {
                BlueprintInteractionService.arm(player, bound, bytes);
            } catch (RuntimeException exception) {
                BOUND.remove(player.getUniqueID(), bound);
                report.errors.add(exception.getMessage());
            }
        }
        return report;
    }

    public static BoundBlueprint get(EntityPlayer player) {
        return BOUND.get(player.getUniqueID());
    }

    public static BoundBlueprint require(EntityPlayer player) {
        BoundBlueprint bound = get(player);
        if (bound == null) throw new IllegalStateException("尚未绑定蓝图，请先使用 /gtbp use <名称> 或 /gtbplocal import <名称>");
        return bound;
    }

    public static void clear(EntityPlayer player) {
        BOUND.remove(player.getUniqueID());
    }

    public static void sendReport(EntityPlayer player, CompatibilityReport report) {
        for (String warning : report.warnings) player.addChatMessage(new ChatComponentText("§e[GTBP] " + warning));
        for (String error : report.errors) player.addChatMessage(new ChatComponentText("§c[GTBP] " + error));
        if (report.canUse()) {
            player.addChatMessage(new ChatComponentText("§a[GTBP] 蓝图验证通过；物质操纵者已切换到粘贴模式"));
            player.addChatMessage(
                new ChatComponentText("§b[GTBP] 右键锁定在方块表面外侧，Shift+右键锁定在方块内部"));
            player.addChatMessage(
                new ChatComponentText("§b[GTBP] 锁定后普通右键可调整设置，Shift+右键开始搭建"));
        }
    }
}

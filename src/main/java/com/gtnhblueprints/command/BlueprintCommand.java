package com.gtnhblueprints.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.MatterBlueprints;
import com.gtnhblueprints.io.BlueprintIO;
import com.gtnhblueprints.model.Blueprint;
import com.gtnhblueprints.model.CompatibilityReport;
import com.gtnhblueprints.network.BlueprintInteractionStatusMessage;
import com.gtnhblueprints.network.BlueprintNetwork;
import com.gtnhblueprints.service.BlueprintBuildService;
import com.gtnhblueprints.service.BlueprintInteractionService;
import com.gtnhblueprints.service.BlueprintLibrary;
import com.gtnhblueprints.service.BoundBlueprint;
import com.gtnhblueprints.service.BlueprintPlanService;
import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer;
import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.RegionAnalysis;
import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.RequiredItemAnalysis;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.utils.ItemId;
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils;

public final class BlueprintCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtbp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtbp <save|list|use|download|info|origin|rotate|mirror|materials|plan|build|cancel>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "save": save(player, args); break;
                case "list": list(player); break;
                case "use": use(player, requiredName(args)); break;
                case "download": download(player, requiredName(args)); break;
                case "info": info(player); break;
                case "origin": origin(player, args); break;
                case "rotate": rotate(player, args); break;
                case "mirror": mirror(player, args); break;
                case "materials": materials(player, args); break;
                case "plan": plan(player, args); break;
                case "build": build(player); break;
                case "cancel": BlueprintInteractionService.cancel(player); break;
                default: throw new WrongUsageException(getCommandUsage(sender));
            }
        } catch (IOException | IllegalStateException | IllegalArgumentException exception) {
            player.addChatMessage(new ChatComponentText("§c[GTBP] " + exception.getMessage()));
        } catch (RuntimeException exception) {
            MatterBlueprints.LOG.error("Unexpected /gtbp command failure", exception);
            String message = exception.getMessage();
            if (message == null || message.trim().isEmpty()) message = "没有附加说明";
            player.addChatMessage(
                new ChatComponentText("§c[GTBP] 内部错误：" + exception.getClass().getSimpleName() + "：" + message));
        }
    }

    private static void save(EntityPlayerMP player, String[] args) throws IOException {
        String name = requiredName(args);
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null || !(held.getItem() instanceof ItemMatterManipulator)) {
            throw new IllegalStateException("请手持物质操纵者");
        }
        ItemMatterManipulator manipulator = (ItemMatterManipulator) held.getItem();
        if ((manipulator.tier.capabilities & ItemMatterManipulator.ALLOW_COPYING) == 0) {
            throw new IllegalStateException("保存蓝图需要支持复制的物质操纵者（MKII 或 MKIII）");
        }

        MMState state = ItemMatterManipulator.getState(held);
        org.joml.Vector3i lookingAt = MMUtils.getLookingAtLocation(player);
        Location a = state.config.getCoordA(player.worldObj, lookingAt);
        Location b = state.config.getCoordB(player.worldObj, lookingAt);
        if (!Location.areCompatible(a, b) || a.worldId != player.worldObj.provider.dimensionId) {
            throw new IllegalStateException("请先用物质操纵者设置复制区域 A/B 点");
        }
        int sizeX = Math.abs(a.x - b.x) + 1;
        int sizeY = Math.abs(a.y - b.y) + 1;
        int sizeZ = Math.abs(a.z - b.z) + 1;
        if (sizeX > BlueprintConfig.maxSpan || sizeY > BlueprintConfig.maxSpan || sizeZ > BlueprintConfig.maxSpan) {
            throw new IllegalStateException("选区尺寸超过限制 " + BlueprintConfig.maxSpan);
        }
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > BlueprintConfig.maxBlocks) throw new IllegalStateException("选区体积超过限制 " + BlueprintConfig.maxBlocks);

        File target = BlueprintIO.resolve(BlueprintLibrary.directory(), name);
        if (target.isFile() && !mayOverwrite(player, name)) throw new IllegalStateException("同名蓝图属于其他玩家，无法覆盖");

        RegionAnalysis analysis = BlockAnalyzer.analyzeRegion(player.worldObj, a, b, true);
        if (analysis == null) throw new IllegalStateException("无法分析选区");
        if (analysis.blocks.size() > BlueprintConfig.maxBlocks) throw new IllegalStateException("蓝图方块数超过限制");
        Blueprint blueprint = Blueprint.capture(name, player, analysis, a);
        byte[] bytes = BlueprintLibrary.save(blueprint);
        CompatibilityReport report = BlueprintLibrary.bind(player, blueprint, bytes);
        BlueprintLibrary.sendReport(player, report);
        player.addChatMessage(
            new ChatComponentText("§a[GTBP] 已保存 " + name + ".gtbp：" + blueprint.blocks.size() + " 方块，" + bytes.length + " 字节"));
        if (blueprint.omittedUnsupportedBlocks > 0) {
            player.addChatMessage(
                new ChatComponentText(
                    "§e[GTBP] 已主动跳过 " + blueprint.omittedUnsupportedBlocks + " 个无法可靠保存附加状态的方块"));
        }
    }

    private static boolean mayOverwrite(EntityPlayerMP player, String name) {
        if (player.canCommandSenderUseCommand(2, "gtbp")) return true;
        try {
            return BlueprintLibrary.load(name).authorUuid().equals(player.getUniqueID());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void list(EntityPlayerMP player) {
        List<String> names = BlueprintLibrary.list();
        player.addChatMessage(new ChatComponentText("§b[GTBP] 服务器蓝图（" + names.size() + "）: " + String.join(", ", names)));
    }

    private static void use(EntityPlayerMP player, String name) throws IOException {
        byte[] bytes = BlueprintLibrary.readBytes(name);
        Blueprint blueprint = BlueprintIO.decode(bytes);
        CompatibilityReport report = BlueprintLibrary.bind(player, blueprint, bytes);
        BlueprintLibrary.sendReport(player, report);
    }

    private static void download(EntityPlayerMP player, String name) throws IOException {
        byte[] bytes = BlueprintLibrary.readBytes(name);
        BlueprintNetwork.download(player, name, bytes);
        player.addChatMessage(new ChatComponentText("§a[GTBP] 正在下载 " + name + "（" + bytes.length + " 字节）"));
    }

    private static void info(EntityPlayerMP player) {
        BoundBlueprint bound = BlueprintLibrary.require(player);
        Blueprint blueprint = bound.blueprint;
        player.addChatMessage(new ChatComponentText(
            "§b[GTBP] " + blueprint.name + " | 作者 " + blueprint.authorName + " | " + blueprint.sizeX + "×" + blueprint.sizeY
                + "×" + blueprint.sizeZ + " | " + blueprint.blocks.size() + " 方块"));
        player.addChatMessage(new ChatComponentText(
            "§b[GTBP] 原点 " + (bound.hasOrigin ? bound.originX + "," + bound.originY + "," + bound.originZ : "未设置")
                + " | 旋转 " + (bound.quarterTurns * 90) + "° | 镜像 " + mirrorName(bound)));
    }

    private static void origin(EntityPlayerMP player, String[] args) {
        BoundBlueprint bound = BlueprintLibrary.require(player);
        if (args.length == 1) {
            org.joml.Vector3i lookingAt = MMUtils.getLookingAtLocation(player);
            bound.originX = lookingAt.x;
            bound.originY = lookingAt.y;
            bound.originZ = lookingAt.z;
        } else if (args.length == 4) {
            bound.originX = Integer.parseInt(args[1]);
            bound.originY = Integer.parseInt(args[2]);
            bound.originZ = Integer.parseInt(args[3]);
        } else {
            throw new IllegalArgumentException("用法: /gtbp origin [x y z]");
        }
        if (bound.originY < 0 || bound.originY > 255) throw new IllegalArgumentException("Y 坐标必须在 0..255");
        bound.worldId = player.worldObj.provider.dimensionId;
        bound.hasOrigin = true;
        BlueprintInteractionService.confirmOrigin(
            player,
            bound,
            "§a[GTBP] 原点已设为 " + bound.originX + ", " + bound.originY + ", " + bound.originZ
                + "；可在规划菜单创建蓝图缺失计划，Shift+右键开始搭建，普通右键可调整设置");
    }

    private static void rotate(EntityPlayerMP player, String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("用法: /gtbp rotate <0|90|180|270>");
        int degrees = Integer.parseInt(args[1]);
        if (degrees % 90 != 0) throw new IllegalArgumentException("旋转角度必须是 90 的倍数");
        BoundBlueprint bound = BlueprintLibrary.require(player);
        bound.quarterTurns = Math.floorMod(degrees / 90, 4);
        BlueprintInteractionService.syncTransform(
            player,
            bound,
            "§a[GTBP] 旋转设为 " + bound.quarterTurns * 90 + "°");
    }

    private static void mirror(EntityPlayerMP player, String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("用法: /gtbp mirror <none|x|z|xz>");
        BoundBlueprint bound = BlueprintLibrary.require(player);
        String value = args[1].toLowerCase(Locale.ROOT);
        if (!Arrays.asList("none", "x", "z", "xz", "zx").contains(value)) throw new IllegalArgumentException("未知镜像模式");
        bound.mirrorX = value.contains("x");
        bound.mirrorZ = value.contains("z");
        BlueprintInteractionService.syncTransform(player, bound, "§a[GTBP] 镜像设为 " + mirrorName(bound));
    }

    private static void materials(EntityPlayerMP player, String[] args) {
        boolean all = args.length >= 2 && "all".equalsIgnoreCase(args[1]);
        BoundBlueprint bound = BlueprintLibrary.require(player);
        MMState state = BlueprintInteractionService.syncOriginFromManipulator(player, bound);
        List<PendingBlock> blocks = bound.createPendingBlocks(false, state.config.transform, state.config.arraySpan);
        RequiredItemAnalysis analysis = BlockAnalyzer.getRequiredItemsForBuild(player, blocks, all);
        List<Map.Entry<ItemId, Long>> items = new ArrayList<>(analysis.requiredItems.entrySet());
        items.sort(Map.Entry.<ItemId, Long>comparingByValue(Comparator.reverseOrder()));
        player.addChatMessage(new ChatComponentText("§b[GTBP] " + (all ? "全部" : "缺失") + "材料种类: " + items.size()));
        int shown = 0;
        for (Map.Entry<ItemId, Long> entry : items) {
            ItemStack stack = entry.getKey().getItemStack();
            String display = stack == null ? entry.getKey().toString() : stack.getDisplayName();
            player.addChatMessage(new ChatComponentText("§7- " + display + " × " + entry.getValue()));
            if (++shown >= 20) break;
        }
        if (items.size() > shown) player.addChatMessage(new ChatComponentText("§7…另有 " + (items.size() - shown) + " 种材料"));
    }

    private static void build(EntityPlayerMP player) {
        BoundBlueprint bound = BlueprintLibrary.require(player);
        BlueprintInteractionService.syncOriginFromManipulator(player, bound);
        BlueprintBuildService.INSTANCE.start(player, bound);
        bound.rightClickArmed = false;
        BlueprintLibrary.clear(player);
        BlueprintNetwork.status(
            player,
            new BlueprintInteractionStatusMessage(BlueprintInteractionStatusMessage.BUILD_STARTED, bound, ""));
    }

    private static void plan(EntityPlayerMP player, String[] args) {
        int flags = 0;
        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if ("auto".equals(mode)) {
                flags = MMUtils.PLAN_AUTO_SUBMIT;
            } else if ("all".equals(mode)) {
                flags = MMUtils.PLAN_ALL;
            } else if ("all-auto".equals(mode) || "auto-all".equals(mode)) {
                flags = MMUtils.PLAN_ALL | MMUtils.PLAN_AUTO_SUBMIT;
            } else if (!"manual".equals(mode) && !"missing".equals(mode)) {
                throw new IllegalArgumentException("用法: /gtbp plan [manual|auto|all|all-auto]");
            }
        }
        BoundBlueprint bound = BlueprintLibrary.require(player);
        BlueprintPlanService.submit(player, bound, flags);
    }

    private static String requiredName(String[] args) throws IOException {
        if (args.length < 2) throw new IOException("缺少蓝图名称");
        return BlueprintIO.safeName(args[1]);
    }

    private static String mirrorName(BoundBlueprint bound) {
        if (bound.mirrorX && bound.mirrorZ) return "XZ";
        if (bound.mirrorX) return "X";
        if (bound.mirrorZ) return "Z";
        return "无";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                args,
                "save",
                "list",
                "use",
                "download",
                "info",
                "origin",
                "rotate",
                "mirror",
                "materials",
                "plan",
                "build",
                "cancel");
        }
        if (args.length == 2 && ("use".equalsIgnoreCase(args[0]) || "download".equalsIgnoreCase(args[0]))) {
            return getListOfStringsFromIterableMatchingLastWord(args, BlueprintLibrary.list());
        }
        if (args.length == 2 && "plan".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "manual", "auto", "all", "all-auto");
        }
        return null;
    }
}

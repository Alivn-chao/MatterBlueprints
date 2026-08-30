package com.gtnhblueprints.service;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.gtnhblueprints.network.BlueprintInteractionStatusMessage;
import com.gtnhblueprints.network.BlueprintNetwork;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PendingAction;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Transform;
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils;

import org.joml.Vector3i;

public final class BlueprintInteractionService {

    private BlueprintInteractionService() {}

    public static void arm(EntityPlayerMP player, BoundBlueprint bound, byte[] bytes) {
        ItemStack stack = requireManipulator(player);
        switchToBlueprintPaste(stack);
        bound.hasOrigin = false;
        bound.worldId = player.worldObj.provider.dimensionId;
        bound.rightClickArmed = true;
        BlueprintNetwork.activate(player, bound.blueprint.name, bytes);
    }

    public static void rightClick(EntityPlayerMP player) {
        BoundBlueprint bound = BlueprintLibrary.get(player);
        try {
            if (bound == null || !bound.rightClickArmed) {
                throw new IllegalStateException("当前没有等待放置的蓝图，请先导入或使用蓝图");
            }
            requireManipulator(player);
            if (!bound.hasOrigin) {
                Vector3i lookingAt = MMUtils.getLookingAtLocation(player);
                if (lookingAt.y < 0 || lookingAt.y > 255) throw new IllegalStateException("Y 坐标必须在 0..255");
                bound.originX = lookingAt.x;
                bound.originY = lookingAt.y;
                bound.originZ = lookingAt.z;
                bound.worldId = player.worldObj.provider.dimensionId;
                bound.hasOrigin = true;
                setManipulatorOrigin(player, bound);
                BlueprintNetwork.status(
                    player,
                    new BlueprintInteractionStatusMessage(
                        BlueprintInteractionStatusMessage.ORIGIN_CONFIRMED,
                        bound,
                        "§a[GTBP] 已锁定原点 " + bound.originX + ", " + bound.originY + ", " + bound.originZ
                            + "；可在规划菜单创建蓝图缺失计划，Shift+右键开始搭建，普通右键可调整设置"));
                return;
            }

            syncOriginFromManipulator(player, bound);
            BlueprintBuildService.INSTANCE.start(player, bound);
            bound.rightClickArmed = false;
            BlueprintLibrary.clear(player);
            BlueprintNetwork.status(
                player,
                new BlueprintInteractionStatusMessage(
                    BlueprintInteractionStatusMessage.BUILD_STARTED,
                    bound,
                    ""));
        } catch (RuntimeException exception) {
            BlueprintNetwork.status(
                player,
                new BlueprintInteractionStatusMessage(
                    BlueprintInteractionStatusMessage.ERROR,
                    bound,
                    "§c[GTBP] " + exception.getMessage()));
        }
    }

    public static void syncTransform(EntityPlayerMP player, BoundBlueprint bound, String text) {
        BlueprintNetwork.status(
            player,
            new BlueprintInteractionStatusMessage(BlueprintInteractionStatusMessage.TRANSFORM_CHANGED, bound, text));
    }

    public static void cancel(EntityPlayerMP player) {
        BoundBlueprint bound = BlueprintLibrary.get(player);
        if (bound != null) bound.rightClickArmed = false;
        BlueprintLibrary.clear(player);
        BlueprintBuildService.INSTANCE.cancel(player, false);
        BlueprintNetwork.status(
            player,
            new BlueprintInteractionStatusMessage(
                BlueprintInteractionStatusMessage.PREVIEW_CLEARED,
                bound,
                "§e[GTBP] 已取消当前蓝图放置"));
    }

    public static void confirmOrigin(EntityPlayerMP player, BoundBlueprint bound, String text) {
        setManipulatorOrigin(player, bound);
        BlueprintNetwork.status(
            player,
            new BlueprintInteractionStatusMessage(BlueprintInteractionStatusMessage.ORIGIN_CONFIRMED, bound, text));
    }

    public static void markArray(EntityPlayerMP player) {
        BoundBlueprint bound = BlueprintLibrary.require(player);
        ItemStack stack = requireManipulator(player);
        MMState state = ItemMatterManipulator.getState(stack).clone();
        if (!bound.rightClickArmed || !bound.hasOrigin) throw new IllegalStateException("请先锁定蓝图原点");
        syncOriginFromState(player, bound, state);

        Location sourceA = new Location(player.worldObj, 0, 0, 0);
        Vector3i deltas = bound.sourceDeltas();
        Location sourceB = new Location(player.worldObj, deltas.x, deltas.y, deltas.z);
        Location destination = new Location(player.worldObj, bound.originX, bound.originY, bound.originZ);
        Vector3i lookingAt = MMUtils.getLookingAtLocation(player);
        state.config.arraySpan = state.config.getArrayMult(
            player.worldObj,
            sourceA,
            sourceB,
            destination,
            lookingAt);
        state.config.action = null;
        state.config.coordC = destination;
        ItemMatterManipulator.setState(stack, state);
        String text = "§a[GTBP] 堆叠设为 " + state.config.arraySpan.x + ", " + state.config.arraySpan.y + ", "
            + state.config.arraySpan.z;
        player.addChatMessage(new net.minecraft.util.ChatComponentText(text));
        syncTransform(player, bound, "");
    }

    public static MMState syncOriginFromManipulator(EntityPlayerMP player, BoundBlueprint bound) {
        ItemStack stack = requireManipulator(player);
        MMState state = ItemMatterManipulator.getState(stack);
        syncOriginFromState(player, bound, state);
        return state;
    }

    private static ItemStack requireManipulator(EntityPlayerMP player) {
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemMatterManipulator)) {
            throw new IllegalStateException("请手持物质操纵者");
        }
        ItemMatterManipulator manipulator = (ItemMatterManipulator) stack.getItem();
        if ((manipulator.tier.capabilities & ItemMatterManipulator.ALLOW_COPYING) == 0) {
            throw new IllegalStateException("需要支持复制的物质操纵者（MKII 或 MKIII）");
        }
        return stack;
    }

    private static void switchToBlueprintPaste(ItemStack stack) {
        MMState state = ItemMatterManipulator.getState(stack).clone();
        state.config.placeMode = PlaceMode.COPYING;
        state.config.coordA = null;
        state.config.coordB = null;
        state.config.coordC = null;
        state.config.coordAOffset = null;
        state.config.coordBOffset = null;
        state.config.coordCOffset = null;
        state.config.action = PendingAction.MARK_PASTE;
        state.config.transform = new Transform();
        state.config.arraySpan = null;
        ItemMatterManipulator.setState(stack, state);
    }

    private static void setManipulatorOrigin(EntityPlayerMP player, BoundBlueprint bound) {
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemMatterManipulator)) return;
        MMState state = ItemMatterManipulator.getState(stack).clone();
        state.config.action = null;
        state.config.coordC = new Location(player.worldObj, bound.originX, bound.originY, bound.originZ);
        state.config.coordCOffset = null;
        ItemMatterManipulator.setState(stack, state);
    }

    private static void syncOriginFromState(EntityPlayerMP player, BoundBlueprint bound, MMState state) {
        Location destination = state.config.getCoordC(player.worldObj, MMUtils.getLookingAtLocation(player));
        if (destination == null || !destination.isInWorld(player.worldObj)) return;
        bound.originX = destination.x;
        bound.originY = destination.y;
        bound.originZ = destination.z;
        bound.worldId = destination.worldId;
        bound.hasOrigin = true;
    }
}

package com.gtnhblueprints.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer;
import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.RequiredItemAnalysis;
import com.recursive_pineapple.matter_manipulator.common.building.IPseudoInventory;
import com.recursive_pineapple.matter_manipulator.common.building.MMInventory;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.utils.BigItemStack;
import com.recursive_pineapple.matter_manipulator.common.utils.ItemId;
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils;

import it.unimi.dsi.fastutil.Pair;

/** Creates Matter Manipulator uplink plans from the currently positioned detached blueprint. */
public final class BlueprintPlanService {

    private BlueprintPlanService() {}

    public static void submit(EntityPlayerMP player, BoundBlueprint bound, int flags) {
        if (!bound.hasOrigin) throw new IllegalStateException("请先锁定蓝图原点");
        if (!bound.rightClickArmed) throw new IllegalStateException("当前蓝图已经不在待搭建状态，请重新导入或使用蓝图");

        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemMatterManipulator)) {
            throw new IllegalStateException("请手持物质操纵者");
        }
        ItemMatterManipulator manipulator = (ItemMatterManipulator) stack.getItem();
        if ((manipulator.tier.capabilities & ItemMatterManipulator.CONNECTS_TO_UPLINK) == 0) {
            throw new IllegalStateException("创建计划需要能够连接量子上行链路的 MKIII 物质操纵者");
        }

        MMState state = BlueprintInteractionService.syncOriginFromManipulator(player, bound).clone();
        boolean all = (flags & MMUtils.PLAN_ALL) != 0;
        boolean autoSubmit = (flags & MMUtils.PLAN_AUTO_SUBMIT) != 0;
        List<PendingBlock> blocks = bound.createPendingBlocks(false, state.config.transform, state.config.arraySpan);
        RequiredItemAnalysis analysis = BlockAnalyzer.getRequiredItemsForBuild(player, blocks, all);
        List<BigItemStack> requested = new ArrayList<>(analysis.requiredItems.size());
        for (Map.Entry<ItemId, Long> entry : analysis.requiredItems.entrySet()) {
            if (entry.getValue() > 0) requested.add(BigItemStack.create(entry.getKey(), entry.getValue()));
        }

        if (requested.isEmpty()) {
            player.addChatMessage(new ChatComponentText("§a[GTBP] 当前投影不缺少需要下单的物品"));
            return;
        }

        if (!all) subtractAvailable(player, state, manipulator, requested);
        if (requested.isEmpty()) {
            player.addChatMessage(new ChatComponentText("§a[GTBP] 玩家库存、ME 网络或上行链路中已有全部所需物品，无需创建计划"));
            return;
        }
        if (!state.connectToUplink()) {
            throw new IllegalStateException("未连接到可用的量子上行链路，请检查 MKIII 绑定和上行链路状态");
        }

        long total = 0;
        for (BigItemStack item : requested) total = saturatedAdd(total, item.getStackSize());
        String planName = bound.blueprint.name + " @ " + bound.originX + "," + bound.originY + "," + bound.originZ;
        state.uplink.submitPlan(player, planName, requested, autoSubmit);
        player.addChatMessage(
            new ChatComponentText(
                "§a[GTBP] 已向量子上行链路提交" + (all ? "全部" : "缺失") + "计划：" + requested.size() + " 种，共 "
                    + total + " 个物品" + (autoSubmit ? "；已自动下单" : "；等待手动请求")));
    }

    private static void subtractAvailable(
        EntityPlayerMP player,
        MMState state,
        ItemMatterManipulator manipulator,
        List<BigItemStack> requested
    ) {
        MMInventory inventory = new MMInventory(player, state, manipulator.tier);
        Pair<Boolean, List<BigItemStack>> result = inventory.tryConsumeItems(
            requested,
            IPseudoInventory.CONSUME_SIMULATED | IPseudoInventory.CONSUME_PARTIAL
                | IPseudoInventory.CONSUME_IGNORE_CREATIVE);
        List<BigItemStack> available = result.right() == null ? new ArrayList<>() : result.right();

        Iterator<BigItemStack> iterator = requested.iterator();
        while (iterator.hasNext()) {
            BigItemStack wanted = iterator.next();
            long found = 0;
            for (BigItemStack present : available) {
                if (present.isSameType(wanted)) found = saturatedAdd(found, present.getStackSize());
            }
            wanted.decStackSize(Math.min(found, wanted.getStackSize()));
            if (wanted.getStackSize() <= 0) iterator.remove();
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}

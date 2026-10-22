package com.gtnhblueprints.service;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

import com.gtnhblueprints.MatterBlueprints;
import com.recursive_pineapple.matter_manipulator.common.building.IBuildable;
import com.recursive_pineapple.matter_manipulator.common.building.PendingMove;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Replace only cross-dimension native move jobs before MM's first placement pulse. */
public final class CrossDimensionMoveService {

    public static final CrossDimensionMoveService INSTANCE = new CrossDimensionMoveService();
    private final Field pendingBuilds;

    private CrossDimensionMoveService() {
        try {
            pendingBuilds = ItemMatterManipulator.class.getDeclaredField("PENDING_BUILDS");
            pendingBuilds.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @SubscribeEvent
    public void onUseTick(PlayerUseItemEvent.Tick event) {
        EntityPlayer player = event.entityPlayer;
        ItemStack stack = event.item;
        if (player.worldObj.isRemote || !(stack.getItem() instanceof ItemMatterManipulator)
            || Integer.MAX_VALUE - event.duration < 2) return;
        try {
            Map<EntityPlayer, IBuildable> jobs = jobs();
            IBuildable nativeJob = jobs.get(player);
            if (!(nativeJob instanceof PendingMove) || nativeJob instanceof CrossDimensionPendingMove) return;
            MMState state = ItemMatterManipulator.getState(stack);
            if (state.config.placeMode != PlaceMode.MOVING || !hasRemoteSelection(state, player.worldObj.provider.dimensionId)) return;
            ItemMatterManipulator item = (ItemMatterManipulator) stack.getItem();
            if ((item.tier.capabilities & ItemMatterManipulator.ALLOW_MOVING) == 0) {
                throw new IllegalArgumentException("当前物质操纵者不支持移动方块");
            }
            if (state.config.action != null || state.config.coordAOffset != null
                || state.config.coordBOffset != null || state.config.coordCOffset != null) {
                throw new IllegalArgumentException("请先确认剪切区域和目标位置");
            }
            CrossDimensionPendingMove move = new CrossDimensionPendingMove(player, state.clone(), item.tier);
            nativeJob.onStopped();
            jobs.put(player, move);
            player.addChatMessage(new ChatComponentText("§b[GTBP] 跨维度移动已就绪；请持续潜行右键，松开即暂停"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            event.setCanceled(true);
            player.stopUsingItem();
            player.addChatMessage(new ChatComponentText("§c[GTBP] 无法跨维度移动：" + exception.getMessage()));
            MatterBlueprints.LOG.warn("Could not prepare cross-dimension MM move", exception);
        }
    }

    private static boolean hasRemoteSelection(MMState state, int dimension) {
        return (state.config.coordA != null && state.config.coordA.worldId != dimension)
            || (state.config.coordB != null && state.config.coordB.worldId != dimension)
            || (state.config.coordC != null && state.config.coordC.worldId != dimension);
    }

    @SuppressWarnings("unchecked")
    private Map<EntityPlayer, IBuildable> jobs() throws IllegalAccessException {
        return (Map<EntityPlayer, IBuildable>) pendingBuilds.get(null);
    }
}

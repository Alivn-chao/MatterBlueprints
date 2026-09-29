package com.gtnhblueprints.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.gtnhblueprints.MatterBlueprints;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBuild;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class BlueprintBuildService {

    public static final BlueprintBuildService INSTANCE = new BlueprintBuildService();
    private static final Field PENDING_BLOCKS = pendingBlocksField();
    private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

    private BlueprintBuildService() {}

    public void start(EntityPlayerMP player, BoundBlueprint bound) {
        cancel(player, false);
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemMatterManipulator)) {
            throw new IllegalStateException("请手持物质操纵者");
        }
        ItemMatterManipulator manipulator = (ItemMatterManipulator) stack.getItem();
        if ((manipulator.tier.capabilities & ItemMatterManipulator.ALLOW_COPYING) == 0) {
            throw new IllegalStateException("需要支持复制的物质操纵者（MKII 或 MKIII）");
        }
        if (bound.worldId != player.worldObj.provider.dimensionId) throw new IllegalStateException("蓝图原点位于另一个维度");

        MMState state = ItemMatterManipulator.getState(stack).clone();
        List<PendingBlock> blocks = bound.createPendingBlocks(true, state.config.transform, state.config.arraySpan);
        ensureInRange(player, manipulator.tier, blocks);
        ensureChunksLoaded(player.worldObj, blocks);

        state.config.placeMode = PlaceMode.COPYING;
        state.config.coordA = null;
        state.config.coordB = null;
        state.config.coordC = null;
        state.config.coordAOffset = null;
        state.config.coordBOffset = null;
        state.config.coordCOffset = null;
        state.config.replaceInterfacesWithP2P = false;

        PendingBuild build = new PendingBuild(player, state, manipulator.tier, blocks);
        Task task = new Task(player, stack, build, manipulator.tier.placeTicks);
        tasks.put(player.getUniqueID(), task);
        player.addChatMessage(new ChatComponentText("§a[GTBP] 开始搭建，共 " + blocks.size() + " 个方块；P2P 频率已按本次搭建重映射"));
    }

    public void cancel(EntityPlayerMP player, boolean notify) {
        Task task = tasks.remove(player.getUniqueID());
        if (task != null) task.stop(notify ? "§e[GTBP] 已取消搭建" : null);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Map.Entry<UUID, Task>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Task> entry = iterator.next();
            Task task = entry.getValue();
            if (task.tick()) tasks.remove(entry.getKey(), task);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) cancel((EntityPlayerMP) event.player, false);
    }

    private static void ensureInRange(EntityPlayerMP player, ManipulatorTier tier, List<PendingBlock> blocks) {
        if (tier.maxRange < 0) return;
        int rangeSquared = tier.maxRange * tier.maxRange;
        Location playerLocation = new Location(
            player.worldObj,
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posY),
            MathHelper.floor_double(player.posZ));
        for (PendingBlock block : blocks) {
            if (block.distanceTo2(playerLocation) > rangeSquared) {
                throw new IllegalStateException("蓝图超出当前物质操纵者的 " + tier.maxRange + " 格范围");
            }
        }
    }

    private static void ensureChunksLoaded(World world, List<PendingBlock> blocks) {
        List<String> missing = new ArrayList<>();
        for (PendingBlock block : blocks) {
            int chunkX = block.x >> 4;
            int chunkZ = block.z >> 4;
            if (!world.getChunkProvider().chunkExists(chunkX, chunkZ)) {
                String value = chunkX + "," + chunkZ;
                if (!missing.contains(value)) missing.add(value);
                if (missing.size() >= 8) break;
            }
        }
        if (!missing.isEmpty()) throw new IllegalStateException("目标区块未加载: " + String.join("; ", missing));
    }

    @SuppressWarnings("unchecked")
    private static Collection<PendingBlock> pendingBlocks(PendingBuild build) {
        try {
            return (Collection<PendingBlock>) PENDING_BLOCKS.get(build);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法读取 Matter Manipulator 0.1.59 的搭建队列", exception);
        }
    }

    private static Field pendingBlocksField() {
        try {
            Field field = PendingBuild.class.getDeclaredField("pendingBlocks");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class Task {

        private final EntityPlayerMP player;
        private final ItemStack stack;
        private final PendingBuild build;
        private final int interval;
        private final List<SkippedBlock> skipped = new ArrayList<>();
        private int ticks;
        private int stalled;
        private boolean stopped;

        private Task(EntityPlayerMP player, ItemStack stack, PendingBuild build, int interval) {
            this.player = player;
            this.stack = stack;
            this.build = build;
            this.interval = Math.max(1, interval);
        }

        private boolean tick() {
            if (stopped || player.playerNetServerHandler == null) return true;
            ItemStack held = player.getCurrentEquippedItem();
            if (held != stack || !(held.getItem() instanceof ItemMatterManipulator)) {
                stop("§e[GTBP] 已暂停：请保持手持开始搭建时的物质操纵者");
                return true;
            }
            if (++ticks % interval != 0) return false;
            try {
                Collection<PendingBlock> pending = pendingBlocks(build);
                ensureChunksLoaded(player.worldObj, new ArrayList<>(pending));
                int before = pending.size();
                build.tryPlaceBlocks(stack, player);
                int after = pending.size();
                if (after == 0) {
                    stop("§a[GTBP] 蓝图搭建完成");
                    return true;
                }
                if (after < before) {
                    stalled = 0;
                    return false;
                }

                int removed = skipImpossibleBlocks(player.worldObj, pending);
                if (removed > 0) {
                    stalled = 0;
                    player.addChatMessage(
                        new ChatComponentText("§e[GTBP] 已跳过 " + removed + " 个当前条件下无法放置的方块"));
                    if (pending.isEmpty()) {
                        stop("§a[GTBP] 蓝图搭建完成（有无法放置的方块被跳过）");
                        return true;
                    }
                    return false;
                }

                stalled++;
                if (stalled >= 40) {
                    stop("§e[GTBP] 搭建停止：连续无进展，剩余 " + after + " 个方块");
                    reportBlocks("§e[GTBP] 仍无法处理的方块：", pending);
                    return true;
                }
                return false;
            } catch (RuntimeException exception) {
                MatterBlueprints.LOG.error("Blueprint build failed", exception);
                stop("§c[GTBP] 搭建失败: " + exception.getMessage());
                return true;
            }
        }

        private int skipImpossibleBlocks(World world, Collection<PendingBlock> pending) {
            int removed = 0;
            Iterator<PendingBlock> iterator = pending.iterator();
            while (iterator.hasNext()) {
                PendingBlock block = iterator.next();
                boolean outsideWorld = block.y < 0 || block.y > 255;
                boolean missingSupport = !outsideWorld
                    && block.spec != null
                    && !block.spec.isAir()
                    && world.isAirBlock(block.x, block.y, block.z)
                    && !block.getBlock().canPlaceBlockAt(world, block.x, block.y, block.z);
                if (!outsideWorld && !missingSupport) continue;
                skipped.add(new SkippedBlock(block));
                iterator.remove();
                removed++;
            }
            return removed;
        }

        private void stop(String message) {
            if (stopped) return;
            stopped = true;
            build.onStopped();
            if (message != null) player.addChatMessage(new ChatComponentText(message));
            if (!skipped.isEmpty()) reportSkipped();
        }

        private void reportSkipped() {
            player.addChatMessage(new ChatComponentText("§e[GTBP] 无法放置并已跳过的方块（共 " + skipped.size() + " 个）："));
            int shown = Math.min(20, skipped.size());
            for (int i = 0; i < shown; i++) {
                SkippedBlock block = skipped.get(i);
                player.addChatMessage(new ChatComponentText("§7- " + block.name + " @ " + block.x + ", " + block.y + ", " + block.z));
            }
            if (skipped.size() > shown) {
                player.addChatMessage(new ChatComponentText("§7…另有 " + (skipped.size() - shown) + " 个"));
            }
        }

        private void reportBlocks(String heading, Collection<PendingBlock> blocks) {
            player.addChatMessage(new ChatComponentText(heading));
            int shown = 0;
            for (PendingBlock block : blocks) {
                player.addChatMessage(
                    new ChatComponentText(
                        "§7- " + displayName(block) + " @ " + block.x + ", " + block.y + ", " + block.z));
                if (++shown >= 20) break;
            }
            if (blocks.size() > shown) {
                player.addChatMessage(new ChatComponentText("§7…另有 " + (blocks.size() - shown) + " 个"));
            }
        }
    }

    private static String displayName(PendingBlock block) {
        ItemStack display = block.getStack();
        if (display != null) return display.getDisplayName();
        if (block.spec != null && block.spec.getObjectId() != null) return block.spec.getObjectId().toString();
        return "未知方块";
    }

    private static final class SkippedBlock {

        private final String name;
        private final int x;
        private final int y;
        private final int z;

        private SkippedBlock(PendingBlock block) {
            name = displayName(block);
            x = block.x;
            y = block.y;
            z = block.z;
        }
    }
}

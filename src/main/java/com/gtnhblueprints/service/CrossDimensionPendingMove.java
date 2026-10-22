package com.gtnhblueprints.service;

import java.util.ArrayDeque;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.MatterBlueprints;
import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;
import com.recursive_pineapple.matter_manipulator.common.building.PendingMove;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.BlockRemoveMode;
import com.recursive_pineapple.matter_manipulator.common.networking.SoundResource;

/** Uses MM's native block movers, but resolves every read and permission against the correct world. */
public final class CrossDimensionPendingMove extends PendingMove {

    private final CrossDimensionMovePlan plan;
    private final ArrayDeque<Integer> retry = new ArrayDeque<>();
    private int cursor;
    private int moved, skipped;
    private long stalledScans;
    private boolean finished, warnedPower;

    public CrossDimensionPendingMove(EntityPlayer player, MMState state, ManipulatorTier tier) {
        super(player, state, tier);
        plan = new CrossDimensionMovePlan(state.config.coordA, state.config.coordB, state.config.coordC,
            BlueprintConfig.maxSpan, BlueprintConfig.maxBlocks);
        if (player.worldObj.provider.dimensionId != plan.targetDimension) {
            throw new IllegalArgumentException("请前往目标维度后开始移动");
        }
        requireLoadedRegion(world(plan.sourceDimension), plan.minX, plan.minZ, plan.maxX, plan.maxZ);
        requireLoadedRegion(world(plan.targetDimension), plan.minX + plan.offsetX, plan.minZ + plan.offsetZ,
            plan.maxX + plan.offsetX, plan.maxZ + plan.offsetZ);
        if (tier.maxRange >= 0) {
            for (int x : new int[] { plan.minX, plan.maxX }) {
                for (int y : new int[] { plan.minY, plan.maxY }) {
                    for (int z : new int[] { plan.minZ, plan.maxZ }) {
                        if (!inRange(plan.target(new Location(plan.sourceDimension, x, y, z)))) {
                            throw new IllegalArgumentException("目标超出当前 MM 的 " + tier.maxRange + " 格范围");
                        }
                    }
                }
            }
        }
    }

    private static World world(int dimension) {
        World world = DimensionManager.getWorld(dimension);
        if (world == null) throw new IllegalStateException("维度 " + dimension + " 未加载；请先加载源区块和目标区块");
        return world;
    }

    private static void requireLoadedRegion(World world, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                requireLoaded(world, x, z);
            }
        }
    }

    private static void requireLoaded(World world, int chunkX, int chunkZ) {
        if (!world.getChunkProvider().chunkExists(chunkX, chunkZ)) {
            throw new IllegalStateException("维度 " + world.provider.dimensionId + " 的区块 " + chunkX + ", " + chunkZ + " 未加载");
        }
    }

    private boolean inRange(Location target) {
        return tier.maxRange < 0 || player.getDistanceSq(target.x, target.y, target.z) <= (double) tier.maxRange * tier.maxRange;
    }

    @Override
    public void tryPlaceBlocks(ItemStack stack, EntityPlayer currentPlayer) {
        if (finished) return;
        try {
            if (currentPlayer != player || player.worldObj.provider.dimensionId != plan.targetDimension) {
                throw new IllegalStateException("玩家已离开目标维度");
            }
            World sourceWorld = world(plan.sourceDimension), targetWorld = world(plan.targetDimension);
            resetWarnings();
            refillPower(stack);
            int budget = Math.max(64, (int) Math.min(4096L, (long) tier.placeSpeed * 8));
            int scans = 0, operations = 0;
            BlockSpec source = new BlockSpec(), target = new BlockSpec();
            while (scans++ < budget && operations < tier.placeSpeed && (cursor < plan.count || !retry.isEmpty())) {
                int index = cursor < plan.count ? cursor++ : retry.removeFirst();
                Location s = plan.source(index), d = plan.target(s);
                requireLoaded(sourceWorld, s.x >> 4, s.z >> 4);
                requireLoaded(targetWorld, d.x >> 4, d.z >> 4);
                if (!inRange(d)) throw new IllegalStateException("目标已超出 MM 的操作范围");
                BlockSpec.fromBlock(source, sourceWorld, s.x, s.y, s.z);
                if (source.isAir()) continue;
                BlockSpec.fromBlock(target, targetWorld, d.x, d.y, d.z);
                boolean targetAir = target.getBlock().isAir(targetWorld, d.x, d.y, d.z);
                boolean replaceable = target.getBlock().isReplaceable(targetWorld, d.x, d.y, d.z);
                // Occupied solid destinations are never destroyed by a remote move, even in ALL mode.
                if ((!targetAir && (!replaceable || state.config.removeMode == BlockRemoveMode.NONE))
                    || source.getBlock().getBlockHardness(sourceWorld, s.x, s.y, s.z) < 0
                    || (!targetAir && target.getBlock().getBlockHardness(targetWorld, d.x, d.y, d.z) < 0)
                    || !isEditable(sourceWorld, s.x, s.y, s.z, false)
                    || !isEditable(targetWorld, d.x, d.y, d.z, true)
                    || (!targetAir && !isEditable(targetWorld, d.x, d.y, d.z, false))) {
                    skipped++;
                    continue;
                }
                if (!source.getBlock().canPlaceBlockAt(targetWorld, d.x, d.y, d.z)) {
                    retry.addLast(index);
                    stalledScans++;
                    continue;
                }
                // Price remote source hardness at its destination distance, not unrelated source coordinates.
                double energy = blockEnergy(sourceWorld, s, source);
                if (!targetAir) energy += blockEnergy(targetWorld, d, target);
                if (!super.tryConsumePower(stack, d.x, d.y, d.z, energy)) {
                    retry.addFirst(index);
                    if (!warnedPower) message("§e[GTBP] 电量不足，等待充电；松开右键可暂停");
                    warnedPower = true;
                    break;
                }
                warnedPower = false;
                if (!targetAir) {
                    removeBlock(targetWorld, d.x, d.y, d.z, target);
                    BlockSpec.fromBlock(target, targetWorld, d.x, d.y, d.z);
                }
                if (PendingMove.moveBlock(this, targetWorld, s, source, d, target)) {
                    moved++;
                    stalledScans = 0;
                    playSound(sourceWorld, s.x, s.y, s.z, SoundResource.MOB_ENDERMEN_PORTAL);
                    playSound(targetWorld, d.x, d.y, d.z, SoundResource.MOB_ENDERMEN_PORTAL);
                } else {
                    skipped++;
                }
                operations++;
            }
            if (cursor == plan.count && retry.isEmpty()) {
                finish("§a[GTBP] 跨维度移动完成：搬运 " + moved + "，跳过 " + skipped + "（被占用或受保护的方块保留在源处）");
            } else if (stalledScans > Math.max(40L * budget, 2L * retry.size())) {
                finish("§e[GTBP] 移动暂停：剩余方块缺少放置支撑；未移动的方块保留在源处");
            }
        } catch (RuntimeException exception) {
            MatterBlueprints.LOG.error("Cross-dimension MM move stopped", exception);
            finish("§c[GTBP] 移动停止：" + exception.getMessage());
        } finally {
            playSounds();
            actuallyGivePlayerStuff();
        }
    }

    private static double blockEnergy(World world, Location location, BlockSpec spec) {
        int hardness = Math.max(0, Math.min(999, (int) spec.getBlock().getBlockHardness(world, location.x, location.y, location.z)));
        return EU_PER_BLOCK * (1 + Math.sqrt(hardness)) * (spec.getBlock().hasTileEntity(spec.getBlockMeta()) ? TE_PENALTY : 1);
    }

    private void message(String text) {
        player.addChatMessage(new ChatComponentText(text));
    }

    private void finish(String text) {
        finished = true;
        message(text);
        player.stopUsingItem();
    }

    @Override
    public void onStopped() {
        finished = true;
        actuallyGivePlayerStuff();
    }

    @Override public int getMoveOffsetX() { return plan.offsetX; }
    @Override public int getMoveOffsetY() { return plan.offsetY; }
    @Override public int getMoveOffsetZ() { return plan.offsetZ; }
    @Override public int getSrcMinX() { return plan.minX; }
    @Override public int getSrcMinY() { return plan.minY; }
    @Override public int getSrcMinZ() { return plan.minZ; }
    @Override public int getSrcMaxX() { return plan.maxX; }
    @Override public int getSrcMaxY() { return plan.maxY; }
    @Override public int getSrcMaxZ() { return plan.maxZ; }
}

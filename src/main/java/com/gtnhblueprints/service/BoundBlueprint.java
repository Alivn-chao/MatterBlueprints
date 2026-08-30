package com.gtnhblueprints.service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnhblueprints.model.Blueprint;
import com.gtnhblueprints.model.BlueprintGeometry;
import com.gtnhblueprints.model.PendingBlockTraits;
import com.gtnhblueprints.BlueprintConfig;
import com.recursive_pineapple.matter_manipulator.common.building.AEAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.AEPartData;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Transform;
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils;

import org.joml.Vector3i;

public final class BoundBlueprint {

    private static final SecureRandom RANDOM = new SecureRandom();

    public final Blueprint blueprint;
    public int originX;
    public int originY;
    public int originZ;
    public int worldId;
    public boolean hasOrigin;
    public int quarterTurns;
    public boolean mirrorX;
    public boolean mirrorZ;
    public boolean rightClickArmed;

    public BoundBlueprint(Blueprint blueprint) {
        this.blueprint = blueprint;
    }

    public List<PendingBlock> createPendingBlocks(boolean remapP2p) {
        return createPendingBlocks(remapP2p, null, null);
    }

    public List<PendingBlock> createPendingBlocks(boolean remapP2p, Transform manipulatorTransform, Vector3i arraySpan) {
        if (!hasOrigin) throw new IllegalStateException("尚未设置蓝图原点");
        List<PendingBlock> blocks = blueprint.instantiate(worldId);
        Transform commandTransform = new Transform();
        commandTransform.flipX = mirrorX;
        commandTransform.flipZ = mirrorZ;
        commandTransform.rotate(ForgeDirection.UP, quarterTurns);

        for (PendingBlock block : blocks) {
            Vector3i position = commandTransform.apply(new Vector3i(block.x, block.y, block.z));
            PendingBlockTraits.transform(block, commandTransform);
            if (manipulatorTransform != null) {
                manipulatorTransform.apply(position);
                PendingBlockTraits.transform(block, manipulatorTransform);
            }
            block.worldId = worldId;
            block.x = originX + position.x;
            block.y = originY + position.y;
            block.z = originZ + position.z;
        }

        if (arraySpan != null) blocks = createArray(blocks, commandTransform, manipulatorTransform, arraySpan);
        if (remapP2p) remapP2pFrequencies(blocks);
        blocks.sort(PendingBlock.getComparator());
        return blocks;
    }

    public Vector3i sourceDeltas() {
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        for (com.gtnhblueprints.model.BlueprintBlock block : blueprint.blocks) {
            minX = Math.min(minX, block.x);
            minY = Math.min(minY, block.y);
            minZ = Math.min(minZ, block.z);
        }
        return new Vector3i(
            BlueprintGeometry.sourceDelta(blueprint.sizeX, blueprint.deltaX, minX),
            BlueprintGeometry.sourceDelta(blueprint.sizeY, blueprint.deltaY, minY),
            BlueprintGeometry.sourceDelta(blueprint.sizeZ, blueprint.deltaZ, minZ));
    }

    private List<PendingBlock> createArray(
        List<PendingBlock> base,
        final Transform commandTransform,
        final Transform manipulatorTransform,
        Vector3i arraySpan
    ) {
        long copies = 1L;
        long[] axes = {
            Math.abs((long) arraySpan.x) + 1L,
            Math.abs((long) arraySpan.y) + 1L,
            Math.abs((long) arraySpan.z) + 1L
        };
        for (long axis : axes) {
            if (copies > BlueprintConfig.maxBlocks / axis) {
                throw new IllegalStateException("堆叠后的蓝图超过方块限制 " + BlueprintConfig.maxBlocks);
            }
            copies *= axis;
        }
        if (base.isEmpty() || copies > BlueprintConfig.maxBlocks / base.size()) {
            throw new IllegalStateException("堆叠后的蓝图超过方块限制 " + BlueprintConfig.maxBlocks);
        }
        final List<PendingBlock> result = new java.util.ArrayList<>((int) (copies * base.size()));
        MMUtils.forEachArrayOffset(arraySpan, sourceDeltas(), offset -> {
            commandTransform.apply(offset);
            if (manipulatorTransform != null) manipulatorTransform.apply(offset);
            for (PendingBlock original : base) {
                PendingBlock copy = PendingBlockTraits.copy(original);
                copy.x += offset.x;
                copy.y += offset.y;
                copy.z += offset.z;
                result.add(copy);
            }
        });
        return result;
    }

    private static void remapP2pFrequencies(List<PendingBlock> blocks) {
        Map<Long, Long> frequencies = new HashMap<>();
        for (PendingBlock block : blocks) {
            if (!(block.ae instanceof AEAnalysisResult)) continue;
            AEAnalysisResult ae = (AEAnalysisResult) block.ae;
            if (ae.mAEParts == null) continue;
            for (AEPartData part : ae.mAEParts) {
                if (part == null || part.mP2PFreq == 0) continue;
                part.mP2PFreq = frequencies.computeIfAbsent(part.mP2PFreq, ignored -> nextFrequency());
            }
        }
    }

    private static long nextFrequency() {
        long value;
        do {
            value = RANDOM.nextLong() & Long.MAX_VALUE;
        } while (value == 0);
        return value;
    }
}

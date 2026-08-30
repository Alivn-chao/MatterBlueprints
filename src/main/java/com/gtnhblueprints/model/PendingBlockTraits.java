package com.gtnhblueprints.model;

import net.minecraftforge.common.util.ForgeDirection;

import com.recursive_pineapple.matter_manipulator.common.building.AEAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;
import com.recursive_pineapple.matter_manipulator.common.building.PortableItemStack;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Transform;

/** Repairs trait-copy gaps in Matter Manipulator 0.1.46 without changing its own classes. */
public final class PendingBlockTraits {

    private PendingBlockTraits() {}

    public static PendingBlock copy(PendingBlock source) {
        PendingBlock copy = source.clone();
        if (source.ae instanceof AEAnalysisResult && copy.ae instanceof AEAnalysisResult) {
            AEAnalysisResult sourceAe = (AEAnalysisResult) source.ae;
            AEAnalysisResult copyAe = (AEAnalysisResult) copy.ae;
            copyAe.mAEFacades = copyStacks(sourceAe.mAEFacades);
        }
        return copy;
    }

    public static void transform(PendingBlock block, Transform transform) {
        PortableItemStack[] facades = null;
        AEAnalysisResult ae = null;
        if (block.ae instanceof AEAnalysisResult) {
            ae = (AEAnalysisResult) block.ae;
            facades = ae.mAEFacades;
        }

        block.transform(transform);

        if (ae != null && facades != null) {
            PortableItemStack[] transformed = new PortableItemStack[facades.length];
            int count = Math.min(facades.length, ForgeDirection.VALID_DIRECTIONS.length);
            for (int i = 0; i < count; i++) {
                ForgeDirection target = transform.apply(ForgeDirection.VALID_DIRECTIONS[i]);
                if (target.ordinal() < transformed.length) transformed[target.ordinal()] = facades[i];
            }
            ae.mAEFacades = transformed;
        }
    }

    private static PortableItemStack[] copyStacks(PortableItemStack[] source) {
        if (source == null) return null;
        PortableItemStack[] result = new PortableItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] == null ? null : source[i].clone();
        }
        return result;
    }
}

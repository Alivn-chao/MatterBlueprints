package com.gtnhblueprints.model;

import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;

/** Audit record describing a replacement that was baked into an exported blueprint. */
public final class BlueprintReplacement {

    public BlockSpec source;
    public BlockSpec target;
    public int affectedBlocks;

    public BlueprintReplacement() {}

    public BlueprintReplacement(BlockSpec source, BlockSpec target, int affectedBlocks) {
        this.source = source == null ? null : source.clone();
        this.target = target == null ? null : target.clone();
        this.affectedBlocks = affectedBlocks;
    }
}

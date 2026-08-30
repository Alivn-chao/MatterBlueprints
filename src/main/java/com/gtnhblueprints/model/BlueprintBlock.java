package com.gtnhblueprints.model;

import net.minecraft.tileentity.TileEntity;

import com.recursive_pineapple.matter_manipulator.common.building.AEAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.ArchitectureCraftAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;
import com.recursive_pineapple.matter_manipulator.common.building.CarpentersBlocksAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.GTAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.InventoryAnalysis;
import com.recursive_pineapple.matter_manipulator.common.building.MultipartAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;

public final class BlueprintBlock {

    public int x;
    public int y;
    public int z;
    public BlockSpec spec;
    public GTAnalysisResult gt;
    public AEAnalysisResult ae;
    public ArchitectureCraftAnalysisResult architecture;
    public MultipartAnalysisResult multipart;
    public CarpentersBlocksAnalysisResult carpenters;
    public TranslocatorAnalysisResult translocator;
    public InventoryAnalysis inventory;
    public int renderOrder;
    public int buildOrder;

    public static BlueprintBlock capture(PendingBlock source) {
        return capture(source, null);
    }

    public static BlueprintBlock capture(PendingBlock source, TileEntity sourceTile) {
        BlueprintBlock block = new BlueprintBlock();
        block.x = source.x;
        block.y = source.y;
        block.z = source.z;
        block.spec = (BlockSpec) source.spec;
        if (source.gt instanceof GTAnalysisResult) block.gt = (GTAnalysisResult) source.gt;
        if (source.ae instanceof AEAnalysisResult) block.ae = (AEAnalysisResult) source.ae;
        if (source.arch instanceof ArchitectureCraftAnalysisResult) {
            block.architecture = (ArchitectureCraftAnalysisResult) source.arch;
        }
        if (source.mp instanceof MultipartAnalysisResult) block.multipart = (MultipartAnalysisResult) source.mp;
        if (source.cb instanceof CarpentersBlocksAnalysisResult) {
            block.carpenters = (CarpentersBlocksAnalysisResult) source.cb;
        }
        if (TranslocatorAnalysisResult.isTranslocator(sourceTile)) {
            block.translocator = TranslocatorAnalysisResult.analyze(sourceTile, source.getStack());
            if (block.translocator == null) return null;
        }
        block.inventory = source.inventory;
        block.renderOrder = source.renderOrder;
        block.buildOrder = source.buildOrder;
        return block;
    }

    public PendingBlock instantiate(int worldId) {
        PendingBlock block = new PendingBlock(worldId, x, y, z, spec);
        block.gt = gt;
        block.ae = ae;
        block.arch = architecture;
        block.mp = multipart;
        block.cb = carpenters;
        if (translocator != null && block.mp == null) block.mp = translocator;
        block.inventory = inventory;
        block.renderOrder = renderOrder;
        block.buildOrder = buildOrder;
        PendingBlock copy = PendingBlockTraits.copy(block);
        InventoryProviderCompatibility.normalize(copy.inventory);
        return copy;
    }
}

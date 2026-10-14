package com.gtnhblueprints.model;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEFrame;
import gregtech.common.blocks.BlockFrameBox;
import gregtech.common.tileentities.machines.multi.nanochip.MTEVacuumConveyorPipe;

import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;

/** Keeps construction items separate from the tile state restored after placement. */
public final class GregTechBlueprintCompatibility {
    private GregTechBlueprintCompatibility() {}

    public static BlockSpec normalize(BlockSpec spec, TileEntity tile) {
        if (spec == null) return null;
        ItemStack stack = spec.toStack(1);
        int metadata = stack == null ? -1 : stack.getItemDamage();
        IMetaTileEntity machine = tile instanceof IGregTechTileEntity
            ? ((IGregTechTileEntity) tile).getMetaTileEntity() : null;
        if (machine == null && spec.getBlock() == GregTechAPI.sBlockMachines) {
            int id = metadata;
            if (id >= 0 && id < GregTechAPI.METATILEENTITIES.length) machine = GregTechAPI.METATILEENTITIES[id];
        }
        if (machine instanceof MTEFrame) {
            MTEFrame frame = (MTEFrame) machine;
            if (frame.mMaterial != null && frame.mMaterial.mMetaItemSubID >= 0 && GregTechAPI.sBlockFrames != null) {
                return withItem(spec, Item.getItemFromBlock(GregTechAPI.sBlockFrames), frame.mMaterial.mMetaItemSubID);
            }
        }
        if (metadata >= 0 && spec.getBlock() instanceof BlockFrameBox) {
            // The high bit denotes a cover/paint tile, not another material or another required item.
            return withItem(spec, Item.getItemFromBlock(spec.getBlock()), metadata & BlockFrameBox.MATERIAL_MASK);
        }
        if (machine instanceof MTEVacuumConveyorPipe) {
            int id = tile instanceof IGregTechTileEntity ? ((IGregTechTileEntity) tile).getMetaTileID() : metadata;
            return withItem(spec, Item.getItemFromBlock(GregTechAPI.sBlockMachines), id);
        }
        return spec;
    }

    private static BlockSpec withItem(BlockSpec source, Item item, int metadata) {
        if (item == null) return source;
        BlockSpec result = new BlockSpec().setObject(item, metadata);
        result.properties = source.properties;
        return result;
    }
}

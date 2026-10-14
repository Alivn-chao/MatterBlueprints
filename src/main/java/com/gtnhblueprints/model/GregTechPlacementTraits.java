package com.gtnhblueprints.model;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.blocks.BlockFrameBox;
import gregtech.common.tileentities.machines.multi.nanochip.MTEVacuumConveyorPipe;

import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext;
import com.recursive_pineapple.matter_manipulator.common.building.GTAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.ITileAnalysisIntegration;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Transform;

/** Runtime adapter; blueprints continue to store the original, portable GT trait format. */
public final class GregTechPlacementTraits implements ITileAnalysisIntegration {
    private final GTAnalysisResult state;

    public GregTechPlacementTraits(GTAnalysisResult state) {
        this.state = state;
    }

    @Override
    public boolean apply(IBlockApplyContext context) {
        TileEntity tile = context.getTileEntity();
        if (tile == null && (state.mGTColour != -1 || state.mCovers != null)
            && context.getWorld().getBlock(context.getX(), context.getY(), context.getZ()) instanceof BlockFrameBox) {
            // MM only creates frame tiles for covers; paint alone also needs a tile.
            BlockFrameBox frame = (BlockFrameBox) context.getWorld().getBlock(context.getX(), context.getY(), context.getZ());
            tile = frame.spawnFrameEntity(context.getWorld(), context.getRealPlayer(), context.getX(), context.getY(), context.getZ());
        }
        GTAnalysisResult applied = state;
        if (tile instanceof IGregTechTileEntity) {
            IGregTechTileEntity base = (IGregTechTileEntity) tile;
            if (base.getMetaTileEntity() instanceof MTEVacuumConveyorPipe) {
                MTEVacuumConveyorPipe pipe = (MTEVacuumConveyorPipe) base.getMetaTileEntity();
                base.setColorization(state.mGTColour);
                // These edges belong to the vacuum network. Replaying old wrench connections can call
                // canConnect on ordinary neighbouring tiles and must not override the new location's topology.
                applied = state.clone();
                applied.mConnections = 0;
                for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                    if (pipe.isConnectedAtSide(side)) applied.mConnections |= side.flag;
                }
            }
        }
        boolean success = applied.apply(context);
        if (success && tile != null) {
            tile.markDirty();
            context.getWorld().markBlockForUpdate(context.getX(), context.getY(), context.getZ());
        }
        return success;
    }

    @Override
    public boolean getRequiredItemsForExistingBlock(IBlockApplyContext context) {
        return state.getRequiredItemsForExistingBlock(context);
    }

    @Override
    public boolean getRequiredItemsForNewBlock(IBlockApplyContext context) {
        return state.getRequiredItemsForNewBlock(context);
    }

    @Override
    public void getItemTag(ItemStack stack) { state.getItemTag(stack); }

    @Override
    public void getItemDetailsChat(List<IChatComponent> details) { state.getItemDetailsChat(details); }

    @Override
    public void transform(Transform transform) { state.transform(transform); }

    @Override
    public GregTechPlacementTraits clone() { return new GregTechPlacementTraits(state.clone()); }

    @Override
    public void migrate() { state.migrate(); }
}

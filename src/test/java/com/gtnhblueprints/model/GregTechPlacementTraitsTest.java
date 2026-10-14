package com.gtnhblueprints.model;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.common.blocks.BlockFrameBox;
import gregtech.common.tileentities.machines.multi.nanochip.MTEVacuumConveyorPipe;

import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext;
import com.recursive_pineapple.matter_manipulator.common.building.GTAnalysisResult;

class GregTechPlacementTraitsTest {
    @Test
    void vacuumPipeUsesDestinationConnectionsAndAppliesPaintBeforeOtherTraits() throws Exception {
        TestWorld world = allocate(TestWorld.class);
        TestTile tile = allocate(TestTile.class);
        TestPipe pipe = allocate(TestPipe.class);
        pipe.setBaseMetaTileEntity(tile);
        world.tile = tile;
        RecordingTraits state = new RecordingTraits();
        state.mGTColour = 12;
        state.mConnections = 63; // Old position was connected on every side.
        assertTrue(new GregTechPlacementTraits(state).apply(context(world)));
        assertEquals(12, tile.colour);
        assertEquals(ForgeDirection.EAST.flag, world.appliedConnections);
        assertEquals(63, state.mConnections, "source topology stays unchanged for the next array copy");
        assertTrue(tile.dirty);
        assertTrue(world.updated);
    }

    @Test
    void paintedFrameWithoutCoversCreatesATileBeforeApplyingTraits() throws Exception {
        TestWorld world = allocate(TestWorld.class);
        world.block = allocate(TestFrame.class);
        RecordingTraits state = new RecordingTraits();
        state.mGTColour = 3;
        assertNull(state.mCovers);
        assertTrue(new GregTechPlacementTraits(state).apply(context(world)));
        assertNotNull(world.tile);
        assertEquals(3, ((TestTile) world.tile).colour);
        assertTrue(world.updated);
    }

    private static IBlockApplyContext context(TestWorld world) {
        return (IBlockApplyContext) Proxy.newProxyInstance(IBlockApplyContext.class.getClassLoader(),
            new Class<?>[] { IBlockApplyContext.class }, (proxy, method, arguments) -> {
                switch (method.getName()) {
                    case "getWorld": return world;
                    case "getTileEntity": return world.tile;
                    case "getRealPlayer": return null;
                    case "getX": case "getY": case "getZ": return 0;
                    default: throw new AssertionError(method.getName());
                }
            });
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) singleton.get(null)).allocateInstance(type));
    }

    private static final class RecordingTraits extends GTAnalysisResult {
        @Override
        public boolean apply(IBlockApplyContext context) {
            TestTile tile = (TestTile) context.getTileEntity();
            assertNotNull(tile);
            if (tile.getMetaTileEntity() instanceof TestPipe) assertEquals(mGTColour, tile.colour);
            tile.setColorization(mGTColour);
            ((TestWorld) context.getWorld()).appliedConnections = mConnections;
            return true;
        }
        @Override
        public RecordingTraits clone() {
            RecordingTraits copy = new RecordingTraits();
            copy.mGTColour = mGTColour;
            copy.mConnections = mConnections;
            return copy;
        }
    }

    private static final class TestTile extends BaseMetaPipeEntity {
        byte colour;
        boolean dirty;
        @Override public byte setColorization(byte value) { colour = value; return value; }
        @Override public void markDirty() { dirty = true; }
    }

    private static final class TestPipe extends MTEVacuumConveyorPipe {
        TestPipe() { super((MTEVacuumConveyorPipe) null); }
        @Override public boolean isConnectedAtSide(ForgeDirection side) { return side == ForgeDirection.EAST; }
    }

    private static final class TestFrame extends BlockFrameBox {
        @Override
        public BaseMetaPipeEntity spawnFrameEntity(World world, net.minecraft.entity.player.EntityPlayer player,
            int x, int y, int z) {
            try {
                TestTile tile = allocate(TestTile.class);
                ((TestWorld) world).tile = tile;
                return tile;
            } catch (Exception e) { throw new AssertionError(e); }
        }
    }

    private static final class TestWorld extends World {
        TileEntity tile;
        Block block;
        int appliedConnections;
        boolean updated;
        TestWorld() { super(null, "test", (net.minecraft.world.WorldProvider) null, null, null); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public Block getBlock(int x, int y, int z) { return block; }
        @Override public void markBlockForUpdate(int x, int y, int z) { updated = true; }
    }
}

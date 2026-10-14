package com.gtnhblueprints.model;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEFrame;
import gregtech.common.blocks.BlockFrameBox;
import gregtech.common.tileentities.machines.multi.nanochip.MTEVacuumConveyorPipe;

import com.gtnhblueprints.io.BlueprintJson;
import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;
import com.recursive_pineapple.matter_manipulator.common.building.GTAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;

class GregTechBlueprintCompatibilityTest {
    private static sun.misc.Unsafe unsafe;
    private static BlockFrameBox frames;
    private static Block machines;
    private static Item frameItem;

    @BeforeAll
    static void initialize() throws Exception {
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        unsafe = (sun.misc.Unsafe) singleton.get(null);
        Field loader = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
        loader.setAccessible(true);
        if (loader.get(null) == null) loader.set(null, unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        Field mods = cpw.mods.fml.common.Loader.class.getDeclaredField("namedMods");
        mods.setAccessible(true);
        if (mods.get(loader.get(null)) == null) mods.set(loader.get(null), new java.util.HashMap<>());
        net.minecraft.init.Bootstrap.func_151354_b();
        frames = (BlockFrameBox) unsafe.allocateInstance(BlockFrameBox.class);
        Field delegate = Block.class.getDeclaredField("delegate");
        delegate.setAccessible(true);
        delegate.set(frames, new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<Block>(frames, Block.class));
        machines = new Block(net.minecraft.block.material.Material.iron) {};
        frameItem = new SubtypeItemBlock(frames);
        Block.blockRegistry.addObject(3900, "blueprint_test_frames", frames);
        Item.itemRegistry.addObject(3900, "blueprint_test_frames", frameItem);
        Block.blockRegistry.addObject(3901, "blueprint_test_machines", machines);
        Item.itemRegistry.addObject(3901, "blueprint_test_machines", new SubtypeItemBlock(machines));
    }

    @Test
    void allMaterialIdsKeepTheirOwnItemAndLoseOnlyTheTileBit() {
        for (int material : new int[] { 1, 16, 26, 81, 303, 999 }) {
            BlockSpec stored = new BlockSpec().setObject(frameItem, material | BlockFrameBox.MTE_BIT);
            BlockSpec decoded = stored.clone();
            ItemStack required = GregTechBlueprintCompatibility.normalize(decoded, null).toStack(1);
            assertSame(frameItem, required.getItem());
            assertEquals(material, required.getItemDamage());
            assertEquals(1, required.stackSize);
            assertNull(required.getTagCompound());
        }
    }

    @Test
    void legacyMachineFrameRequestsTheCurrentCraftableFrame() throws Exception {
        Block previousMachines = GregTechAPI.sBlockMachines;
        Block previousFrames = GregTechAPI.sBlockFrames;
        Materials steel = (Materials) unsafe.allocateInstance(Materials.class);
        Field materialId = Materials.class.getDeclaredField("mMetaItemSubID");
        materialId.setAccessible(true);
        materialId.set(steel, 305);
        int id = 4096 + steel.mMetaItemSubID;
        IMetaTileEntity previous = GregTechAPI.METATILEENTITIES[id];
        try {
            GregTechAPI.sBlockMachines = machines;
            GregTechAPI.sBlockFrames = frames;
            MTEFrame prototype = (MTEFrame) unsafe.allocateInstance(MTEFrame.class);
            Field material = MTEFrame.class.getDeclaredField("mMaterial");
            material.setAccessible(true);
            material.set(prototype, steel);
            GregTechAPI.METATILEENTITIES[id] = prototype;
            BlueprintBlock stored = new BlueprintBlock();
            stored.spec = new BlockSpec().setObject(Item.getItemFromBlock(machines), id);
            stored.gt = new GTAnalysisResult();
            stored.gt.mGTColour = 4;
            BlueprintBlock decoded = stored;
            decoded.gt = BlueprintJson.GSON.fromJson(BlueprintJson.GSON.toJson(stored.gt), GTAnalysisResult.class);
            PendingBlock pending = decoded.instantiate(0);
            ItemStack required = pending.getStack();
            assertSame(frameItem, required.getItem());
            assertEquals(steel.mMetaItemSubID, required.getItemDamage());
            assertTrue(pending.gt instanceof GregTechPlacementTraits);
            assertEquals(4, decoded.gt.mGTColour);
            assertEquals(id, decoded.spec.toStack(1).getItemDamage(), "migration must not mutate the stored source");
        } finally {
            GregTechAPI.sBlockMachines = previousMachines;
            GregTechAPI.sBlockFrames = previousFrames;
            GregTechAPI.METATILEENTITIES[id] = previous;
        }
    }

    @Test
    void everyPipeColourRequiresTheSamePlainPipeItem() throws Exception {
        Block previousMachines = GregTechAPI.sBlockMachines;
        int id = 30000;
        IMetaTileEntity previous = GregTechAPI.METATILEENTITIES[id];
        try {
            GregTechAPI.sBlockMachines = machines;
            GregTechAPI.METATILEENTITIES[id] = (MTEVacuumConveyorPipe) unsafe.allocateInstance(MTEVacuumConveyorPipe.class);
            for (byte colour = -1; colour < 16; colour++) {
                BlueprintBlock stored = new BlueprintBlock();
                stored.spec = new BlockSpec().setObject(Item.getItemFromBlock(machines), id);
                stored.gt = new GTAnalysisResult();
                stored.gt.mGTColour = colour;
                BlueprintBlock decoded = stored;
            decoded.gt = BlueprintJson.GSON.fromJson(BlueprintJson.GSON.toJson(stored.gt), GTAnalysisResult.class);
                ItemStack required = decoded.instantiate(0).getStack();
                assertEquals(id, required.getItemDamage());
                assertNull(required.getTagCompound());
                assertEquals(colour, decoded.gt.mGTColour);
            }
        } finally {
            GregTechAPI.sBlockMachines = previousMachines;
            GregTechAPI.METATILEENTITIES[id] = previous;
        }
    }

    @Test
    void unrelatedBlocksAreUntouched() {
        BlockSpec source = new BlockSpec().setObject(Item.getItemFromBlock(Blocks.wool), 7);
        assertSame(source, GregTechBlueprintCompatibility.normalize(source, null));
    }

    private static final class SubtypeItemBlock extends ItemBlock {
        SubtypeItemBlock(Block block) { super(block); setHasSubtypes(true); }
        @Override
        public int getMetadata(int metadata) { return metadata; }
    }
}

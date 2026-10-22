package com.gtnhblueprints.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;

/** Guard the private native integration and Forge ordering; no running client/server is needed. */
class CrossDimensionMoveCompatibilityTest {

    @Test
    void nativeMoveQueueStillUsesEntityPlayerKeysAndBuildableValues() throws Exception {
        java.lang.reflect.Field queue = ItemMatterManipulator.class.getDeclaredField("PENDING_BUILDS");
        assertTrue(Modifier.isStatic(queue.getModifiers()));
        assertTrue(Map.class.isAssignableFrom(queue.getType()));
        assertTrue(queue.getGenericType().getTypeName().contains(EntityPlayer.class.getName()));
        assertTrue(queue.getGenericType().getTypeName().contains("IBuildable"));
    }

    @Test
    void forgeUseTickRunsBeforeNativeItemUseTick() throws Exception {
        MethodNode tick = method("net/minecraft/entity/player/EntityPlayer", "onUpdate");
        int event = callIndex(tick, "onItemUseTick"), item = callIndex(tick, "onUsingTick");
        assertTrue(event >= 0 && item > event, "The queue replacement must run before MM's placement pulse");
    }

    @Test
    void nativeItemCreatesTheMoveQueueBeforeItsPlacementPulse() throws Exception {
        MethodNode tick = method("com/recursive_pineapple/matter_manipulator/common/items/manipulator/ItemMatterManipulator", "onUsingTick");
        int create = callIndex(tick, "getPendingMove"), place = callIndex(tick, "tryPlaceBlocks");
        assertTrue(create >= 0 && place > create);
        assertTrue(java.util.Arrays.stream(tick.instructions.toArray())
            .anyMatch(i -> i instanceof org.objectweb.asm.tree.IntInsnNode
                && ((org.objectweb.asm.tree.IntInsnNode) i).operand == 10), "Native placement must retain its startup delay");
    }

    @Test
    void nativeMoverKeepsSeparateWorldsAndExistingTileMoverHooks() throws Exception {
        MethodNode move = method("com/recursive_pineapple/matter_manipulator/common/building/PendingMove", "moveBlock");
        assertTrue(Modifier.isStatic(move.access));
        long worlds = java.util.Arrays.stream(move.instructions.toArray())
            .filter(i -> i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("getWorld")).count();
        assertEquals(2, worlds);
        assertTrue(callIndex(move, "getBlockMover") >= 0);
        assertTrue(callIndex(move, "allowTelepose") >= 0);
        assertTrue(callIndex(move, "captureDrops") >= 0);
        assertTrue(callIndex(move, "stopCapturingDrops") >= 0);
    }

    private static int callIndex(MethodNode method, String name) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int i = 0; i < instructions.length; i++) {
            if (instructions[i] instanceof MethodInsnNode && ((MethodInsnNode) instructions[i]).name.equals(name)) return i;
        }
        return -1;
    }

    private static MethodNode method(String owner, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = CrossDimensionMoveCompatibilityTest.class.getResourceAsStream("/" + owner + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        return node.methods.stream().map(m -> (MethodNode) m).filter(m -> m.name.equals(name))
            .findFirst().orElseThrow(AssertionError::new);
    }
}

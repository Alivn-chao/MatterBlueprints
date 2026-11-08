package com.gtnhblueprints.wireless;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import com.gtnhblueprints.loading.HostedLateMixinLoader;
import gregtech.api.enums.MetaTileEntityIDs;

class WirelessMECompatibilityTest {
    @Test void unlimitedHooksCoverBothNodeCapacityAndTheNativeControllerFaceLimit() throws Exception {
        ClassNode calculation = read("appeng/me/pathfinding/PathingCalculation");
        int faceLimits = 0;
        for (MethodNode method : calculation.methods) {
            if (!method.name.equals("<init>")) continue;
            assertEquals("(Lappeng/api/networking/IGrid;)V", method.desc);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof org.objectweb.asm.tree.IntInsnNode
                    && ((org.objectweb.asm.tree.IntInsnNode) instruction).operand == 32) faceLimits++;
            }
        }
        assertEquals(1, faceLimits);
        ClassNode grid = read("appeng/me/GridNode");
        assertTrue(grid.methods.stream().anyMatch(m -> m.name.equals("getMaxChannels") && m.desc.equals("()I")));
        ClassNode path = read("appeng/me/cache/PathGridCache");
        for (String name : Arrays.asList("addNode", "removeNode")) {
            assertTrue(path.methods.stream().anyMatch(m -> m.name.equals(name)
                && m.desc.equals("(Lappeng/api/networking/IGridNode;Lappeng/api/networking/IGridHost;)V")));
        }
        java.util.List<String> loaded = new HostedLateMixinLoader()
            .getMixins(java.util.Collections.singleton("appliedenergistics2"));
        assertTrue(loaded.contains("MixinAEWirelessControllerClass"));
        assertTrue(loaded.contains("MixinAEWirelessPathGrid"));
        assertTrue(loaded.contains("MixinAEWirelessPathCalculation"));
    }

    private ClassNode read(String name) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    @Test void wirelessControllerRemainsAnAEControllerAndHasSeparateWorldNbtHooks() throws Exception {
        ClassNode controller = read("com/gtnhblueprints/wireless/TileWirelessMEController");
        assertEquals("appeng/tile/networking/TileController", controller.superName);
        assertTrue(controller.methods.stream().anyMatch(m -> m.name.equals("readWireless") && m.visibleAnnotations != null));
        assertTrue(controller.methods.stream().anyMatch(m -> m.name.equals("writeWireless") && m.visibleAnnotations != null));
        // AE's native structure validator accepts TileController subclasses.
        ClassNode validator = read("appeng/me/pathfinding/ControllerValidator"); boolean controllerCheck = false;
        for (MethodNode m : validator.methods) for (AbstractInsnNode i : m.instructions.toArray())
            if (i instanceof TypeInsnNode && ((TypeInsnNode) i).desc.equals("appeng/tile/networking/TileController")) controllerCheck = true;
        assertTrue(controllerCheck);
    }
    @Test void exactClassControllerIndexHookMatchesAEAndLoadsOnlyWithAE() throws Exception {
        ClassNode grid = read("appeng/me/GridNode");
        assertTrue(grid.methods.stream().anyMatch(m -> m.name.equals("getMachineClass") && m.desc.equals("()Ljava/lang/Class;")));
        HostedLateMixinLoader loader = new HostedLateMixinLoader();
        assertTrue(loader.getMixins(new java.util.HashSet<>(Arrays.asList("gregtech", "appliedenergistics2"))).contains("MixinAEWirelessControllerClass"));
        assertFalse(loader.getMixins(java.util.Collections.singleton("gregtech")).contains("MixinAEWirelessControllerClass"));
    }
    @Test void wirelessPortsRetainNativePatternAndOutputImplementationsAndUseFreeIds() throws Exception {
        for (String[] pair : new String[][] {
            { "MTEWirelessPatternInputAssembly", "gregtech/common/tileentities/machines/MTEHatchCraftingInputME" },
            { "MTEWirelessOutputBus", "gregtech/common/tileentities/machines/outputme/MTEHatchOutputBusME" },
            { "MTEWirelessOutputHatch", "gregtech/common/tileentities/machines/outputme/MTEHatchOutputME" },
            { "MTEWirelessOutputAssembly", "com/gtnhblueprints/machine/MTEMEOutputAssembly" },
            { "MTEWirelessInputAssembly", "com/gtnhblueprints/machine/MTEMEInputAssembly" } }) {
            assertEquals(pair[1], read("com/gtnhblueprints/wireless/" + pair[0]).superName);
        }
        for (int id = 32605; id <= 32609; id++) for (MetaTileEntityIDs official : MetaTileEntityIDs.values()) assertNotEquals(id, official.ID);
    }
    @Test void patternRefundAndOutputCacheFlushRunBeforeWirelessDisconnect() throws Exception {
        for (String name : Arrays.asList("MTEWirelessPatternInputAssembly", "MTEWirelessOutputBus", "MTEWirelessOutputHatch", "MTEWirelessOutputAssembly", "MTEWirelessInputAssembly")) {
            MethodNode method = read("com/gtnhblueprints/wireless/" + name).methods.stream().filter(m -> m.name.equals("onBlockDestroyed")).findFirst().get();
            int order = 0; boolean nativeDestroy = false;
            for (AbstractInsnNode i : method.instructions.toArray()) if (i instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) i;
                if (call.name.equals("onBlockDestroyed")) nativeDestroy = true;
                if (call.name.equals("removed")) { assertTrue(nativeDestroy, name); order++; }
            }
            assertEquals(1, order);
        }
    }
}

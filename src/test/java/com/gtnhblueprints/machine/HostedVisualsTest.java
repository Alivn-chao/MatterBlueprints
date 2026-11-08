package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import com.gtnhblueprints.mixin.MixinHostedMachineVisuals;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

class HostedVisualsTest {
    static class Harness extends MixinHostedMachineVisuals {}

    @Test
    void visualLeaseDoesNotModifyTheRealRecipeClockAndExpiresWithoutHostUpdates() {
        assertEquals(900, HostedVisuals.displayProgress(900, false, false, false, 0, 100));
        assertEquals(1, HostedVisuals.displayProgress(0, true, true, true, 120, 100));
        assertEquals(1, HostedVisuals.displayProgress(0, true, true, true, 120, 120));
        assertEquals(0, HostedVisuals.displayProgress(0, true, true, true, 120, 121));
        assertEquals(0, HostedVisuals.displayProgress(0, true, true, false, 120, 100));
        assertEquals(0, HostedVisuals.displayProgress(900, true, false, true, 120, 100));
    }

    @Test
    void shortRecipeHandoffsKeepTheLeaseButExplicitOffAndUnbindDoNot() throws Exception {
        Harness state = new Harness();
        state.matterblueprints$visual(true, true, 120);
        for (int tick = 101; tick < 110; tick++) state.matterblueprints$visual(true, false, tick + 20);
        assertEquals(120L, field("matterblueprints$visualUntil").getLong(state));
        assertTrue(field("matterblueprints$activeVisual").getBoolean(state));
        state.matterblueprints$visual(true, true, 130);
        assertEquals(130L, field("matterblueprints$visualUntil").getLong(state));
        state.matterblueprints$visual(true, false, 0);
        assertFalse(field("matterblueprints$activeVisual").getBoolean(state));
        state.matterblueprints$visual(false, false, 0);
        assertFalse(field("matterblueprints$managedVisual").getBoolean(state));
    }

    @Test
    void oneHundredTwentyEightMachinesOnlySetActiveOnTransitions() throws Exception {
        Method sync = MixinHostedMachineVisuals.class.getDeclaredMethod("matterblueprints$syncOnlyTransitions",
            IGregTechTileEntity.class, boolean.class);
        sync.setAccessible(true);
        int[] changes = { 0 };
        for (int machine = 0; machine < 128; machine++) {
            Harness state = new Harness();
            state.matterblueprints$visual(true, true, 1220);
            boolean[] active = { false };
            IGregTechTileEntity tile = (IGregTechTileEntity) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IGregTechTileEntity.class }, (proxy, method, args) -> {
                    if (method.getName().equals("isActive")) return active[0];
                    if (method.getName().equals("setActive")) { active[0] = (Boolean) args[0]; changes[0]++; return null; }
                    throw new AssertionError(method.getName());
                });
            for (int tick = 0; tick < 1200; tick++) sync.invoke(state, tile, true);
            sync.invoke(state, tile, false);
            sync.invoke(state, tile, false);
        }
        assertEquals(128 * 2, changes[0]);
    }

    @Test
    void nativeHooksTargetOnlyTheTwoVisualReadsAndKeepClientAnimationPaths() throws Exception {
        ClassNode base = read("gregtech/api/metatileentity/implementations/MTEMultiBlockBase");
        MethodNode post = method(base, "onPostTick");
        int progressReads = 0, activeWrites = 0;
        boolean coils = false, recipeCall = false;
        for (AbstractInsnNode instruction : post.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.name.equals("mMaxProgresstime") && field.getOpcode() == Opcodes.GETFIELD) progressReads++;
            }
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.name.equals("setActive")) activeWrites++;
                coils |= call.name.equals("tryActivateCoilLease");
                recipeCall |= call.name.equals("runMachine");
            }
        }
        assertEquals(2, progressReads); assertEquals(1, activeWrites);
        assertTrue(coils); assertTrue(recipeCall);
        assertTrue(calls(method(base, "tryActivateCoilLease"), "activate"));
        assertTrue(calls(method(read("gregtech/common/tileentities/machines/multi/MTEIndustrialCuttingMachine"), "onPostTick"), "isActive"));
        assertTrue(calls(method(read("gregtech/common/tileentities/machines/multi/foundry/MTEExoFoundry"), "renderTESR"), "isActive"));
        assertTrue(calls(method(base, "runMachine"), "onRunningTick"));
    }

    private static Field field(String name) throws Exception {
        Field field = MixinHostedMachineVisuals.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static boolean calls(MethodNode method, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals(name)) return true;
        }
        return false;
    }
    private static MethodNode method(ClassNode type, String name) {
        for (Object value : type.methods) { MethodNode method = (MethodNode) value; if (method.name.equals(name)) return method; }
        throw new AssertionError(name);
    }
    private static ClassNode read(String name) throws Exception {
        JarURLConnection connection = (JarURLConnection) HostedVisualsTest.class.getResource("/" + name + ".class").openConnection();
        ClassNode node = new ClassNode();
        try (ZipFile zip = new ZipFile(new java.io.File(connection.getJarFileURL().toURI()));
            InputStream stream = zip.getInputStream(zip.getEntry(name + ".class"))) {
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }
}

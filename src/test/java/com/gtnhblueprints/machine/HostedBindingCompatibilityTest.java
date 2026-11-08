package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnhblueprints.io.BlueprintJson;
import com.gtnhblueprints.loading.HostedLateMixinLoader;
import com.gtnhblueprints.mixin.MixinMMHostedBinding;
import com.recursive_pineapple.matter_manipulator.common.building.GTAnalysisResult;

class HostedBindingCompatibilityTest {
    static class TraitHarness extends MixinMMHostedBinding {}
    static class ResultHarness extends GTAnalysisResult implements HostedBindingSupport.Trait {
        private String host;
        @Override public String matterblueprints$getHost() { return host; }
        @Override public void matterblueprints$setHost(String id) { host = id; }
    }

    @Test
    void cloneCallbackAndJsonPreserveHostWithoutMutableSharedState() throws Exception {
        TraitHarness source = new TraitHarness(); source.matterblueprints$setHost("center-id");
        ResultHarness result = new ResultHarness();
        Method cloneHook = MixinMMHostedBinding.class.getDeclaredMethod("matterblueprints$copy", CallbackInfoReturnable.class);
        cloneHook.setAccessible(true);
        cloneHook.invoke(source, new CallbackInfoReturnable<GTAnalysisResult>("clone", false, result));
        assertEquals("center-id", result.matterblueprints$getHost());
        result.matterblueprints$setHost("another-center");
        assertEquals("center-id", source.matterblueprints$getHost());
        String json = BlueprintJson.GSON.toJson(source);
        assertTrue(json.contains("matterblueprints$hostId"));
        assertEquals("center-id", BlueprintJson.GSON.fromJson(json, TraitHarness.class).matterblueprints$getHost());
        assertNull(BlueprintJson.GSON.fromJson("{}", TraitHarness.class).matterblueprints$getHost());
    }

    @Test
    void equalityCallbackSeparatesDifferentCentersAndNoOpTraits() throws Exception {
        TraitHarness source = new TraitHarness(); source.matterblueprints$setHost("center-id");
        ResultHarness other = new ResultHarness();
        Method hook = MixinMMHostedBinding.class.getDeclaredMethod("matterblueprints$equals", Object.class, CallbackInfoReturnable.class);
        hook.setAccessible(true);
        CallbackInfoReturnable<Boolean> noOp = new CallbackInfoReturnable<>("equals", true, true);
        hook.invoke(source, other, noOp);
        assertFalse(noOp.getReturnValue());
        other.matterblueprints$setHost("center-id");
        CallbackInfoReturnable<Boolean> same = new CallbackInfoReturnable<>("equals", true, true);
        hook.invoke(source, other, same);
        assertTrue(same.getReturnValue());
        CallbackInfoReturnable<Boolean> nativeMismatch = new CallbackInfoReturnable<>("equals", true, false);
        hook.invoke(source, other, nativeMismatch);
        assertFalse(nativeMismatch.getReturnValue());
    }

    @Test
    void installedNativeVersionsHaveAllRequiredHookTargets() throws Exception {
        ClassNode analysis = read("com/recursive_pineapple/matter_manipulator/common/building/GTAnalysisResult");
        assertMethod(analysis, "<init>", "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;)V");
        assertMethod(analysis, "apply", "(Lcom/recursive_pineapple/matter_manipulator/common/building/BlockAnalyzer$IBlockApplyContext;)Z");
        assertMethod(analysis, "clone", "()Lcom/recursive_pineapple/matter_manipulator/common/building/GTAnalysisResult;");
        assertMethod(analysis, "equals", "(Ljava/lang/Object;)Z");
        assertMethod(analysis, "hashCode", "()I");
        assertMethod(read("gregtech/common/blocks/BlockMachines"), "breakBlock", "(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;I)V");
        assertMethod(read("com/recursive_pineapple/matter_manipulator/common/building/PendingMove"), "moveBlock",
            "(Lcom/recursive_pineapple/matter_manipulator/common/building/PendingMove;Lnet/minecraft/world/World;"
                + "Lcom/recursive_pineapple/matter_manipulator/common/items/manipulator/Location;"
                + "Lcom/recursive_pineapple/matter_manipulator/common/building/BlockSpec;"
                + "Lcom/recursive_pineapple/matter_manipulator/common/items/manipulator/Location;"
                + "Lcom/recursive_pineapple/matter_manipulator/common/building/BlockSpec;)Z");
        assertTrue(new HostedLateMixinLoader().getMixins(new HashSet<>(Arrays.asList("gregtech", "matter-manipulator")))
            .containsAll(Arrays.asList("MixinMMHostedBinding", "MixinGTHostedBindingRemoval", "MixinMMHostedMove")));
    }

    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        JarURLConnection connection = (JarURLConnection) HostedBindingCompatibilityTest.class
            .getResource("/" + name + ".class").openConnection();
        // GT has newer-Java multi-release entries. Validate the Java 8 classes loaded by the game.
        try (ZipFile zip = new ZipFile(new java.io.File(connection.getJarFileURL().toURI()));
            InputStream stream = zip.getInputStream(zip.getEntry(name + ".class"))) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
        }
        return node;
    }

    private static void assertMethod(ClassNode node, String name, String descriptor) {
        assertTrue(node.methods.stream().anyMatch(value -> {
            MethodNode method = (MethodNode) value;
            return method.name.equals(name) && method.desc.equals(descriptor);
        }), node.name + "." + name + descriptor);
    }
}

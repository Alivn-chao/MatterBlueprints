package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class HostedAntimatterCompatibilityTest {

    private static final String FORGE = "goodgenerator/blocks/tileEntity/AntimatterForge";

    @Test
    void beta3HasExactlyTheTwoEnergyCallsRedirectedByTheMixin() throws Exception {
        ClassNode source = read(FORGE);
        for (String name : Arrays.asList("onPostTick", "checkProcessing")) {
            MethodNode method = method(source, name);
            int withdrawals = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!call.name.equals("drainEnergyInput")) continue;
                assertEquals(FORGE, call.owner);
                assertEquals("(J)Z", call.desc);
                withdrawals++;
            }
            assertEquals(1, withdrawals, name);
        }
        ClassNode mixin = read("com/gtnhblueprints/mixin/MixinAntimatterForge");
        assertTrue(mixin.interfaces.contains("com/gtnhblueprints/machine/HostedAntimatterSupport$Patched"));
        for (String name : Arrays.asList("onPostTick", "checkProcessing", "calculateContainedAntimatter", "decimateAntimatter")) {
            boolean readsHatches = false;
            for (AbstractInsnNode instruction : method(source, name).instructions.toArray()) {
                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    if (field.name.equals("amOutputHatches")) {
                        assertEquals(FORGE, field.owner);
                        assertEquals("Ljava/util/List;", field.desc);
                        assertEquals(Opcodes.GETFIELD, field.getOpcode());
                        readsHatches = true;
                    }
                }
            }
            assertTrue(readsHatches, name);
        }
    }

    @Test
    void nativeProductionMutatesRealHatchesInsteadOfQueuingRecipeOutputs() throws Exception {
        ClassNode source = read(FORGE);
        MethodNode cycle = method(source, "checkProcessing");
        assertTrue(calls(cycle, "distributeAntimatterToHatch"));
        assertTrue(calls(cycle, "decimateAntimatter"));
        assertTrue(calls(cycle, "depleteInput"));
        for (AbstractInsnNode instruction : cycle.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                assertFalse(field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("mOutputFluids"));
            }
        }
        MethodNode distribution = method(source, "distributeAntimatterToHatch");
        assertTrue(calls(distribution, "fill"));
        assertTrue(calls(distribution, "drain"));
        assertTrue(calls(distribution, "nextGaussian"));
    }

    @Test
    void nativeContainmentKeepsItsBaseCostAndRollingHistory() throws Exception {
        Object pricing = pricingHarness();
        assertEquals(10_000_000L, price(pricing, "calculateEnergyContainmentCost", 0));
        assertEquals(10_001_000L, price(pricing, "calculateEnergyContainmentCost", 100));
        assertEquals(10_002_828L, price(pricing, "calculateEnergyContainmentCost", 100));
        assertEquals(200L, field(pricing, "rollingCost").getLong(pricing));
        price(pricing, "calculateEnergyContainmentCost", 0);
        assertEquals(199L, field(pricing, "rollingCost").getLong(pricing));
        // A residual history below 100 is reset, as in native GT.
        field(pricing, "rollingCost").setLong(pricing, 50L);
        assertEquals(10_000_000L, price(pricing, "calculateEnergyContainmentCost", 0));
    }

    @Test
    void nativeCycleIsAnInstantaneousChargeAndRespectsGravityFluid() throws Exception {
        Object pricing = pricingHarness();
        assertEquals(0L, price(pricing, "calculateEnergyCost", 0));
        assertEquals(1_000_000_000L, price(pricing, "calculateEnergyCost", 100));
        float[] modifiers = (float[]) field(pricing, "modifiers").get(pricing);
        modifiers[1] = -0.1F;
        assertEquals((long) Math.pow(1_000_000D, 1.5F - 0.1F), price(pricing, "calculateEnergyCost", 100));
    }

    @Test
    void prepaidCooldownSurvivesSaveWithoutBecomingPendingOutputOrRecurringPower() {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setBoolean("antimatterCycle", true);
        saved.setInteger("progress", 7);
        saved.setInteger("maxProgress", 20);
        saved.setInteger("parallels", 1);
        saved.setLong("nextRecipeCheckTick", 120);
        HostedMachineCoordinator.HostedJob job = HostedMachineCoordinator.HostedJob.readFromNBT(saved);
        assertTrue(job.antimatterCycle);
        assertTrue(job.isActive());
        NBTTagCompound rewritten = new NBTTagCompound();
        job.writeToNBT(rewritten);
        assertEquals(7, rewritten.getInteger("progress"));
        assertEquals(20, rewritten.getInteger("maxProgress"));
        assertEquals(120L, rewritten.getLong("nextRecipeCheckTick"));
        assertEquals(0L, rewritten.getLong("energyUsage"));
        assertEquals(0, rewritten.getInteger("fluidOutputCount"));
        job.clear();
        assertFalse(job.antimatterCycle);
        assertFalse(job.isActive());
    }

    @Test
    void oneSharedRollScalesBothPositiveAndNegativeChangesAndCosts() {
        assertEquals(120, HostedAntimatterBatch.scaleChange(30, 4));
        assertEquals(-120, HostedAntimatterBatch.scaleChange(-30, 4));
        assertEquals(4_000_000L, HostedAntimatterBatch.scaleCost(1_000_000L, 4));
        assertEquals(0L, HostedAntimatterBatch.scaleCost(0L, 4));
        assertEquals(Long.MAX_VALUE, HostedAntimatterBatch.scaleCost(Long.MAX_VALUE, 4));
        long positive = (long) HostedAntimatterBatch.scaleChange(Integer.MAX_VALUE, 100) * 16;
        long negative = (long) HostedAntimatterBatch.scaleChange(Integer.MIN_VALUE, 100) * 16;
        assertTrue(positive <= Integer.MAX_VALUE);
        assertTrue(negative > Integer.MIN_VALUE);
    }

    @Test
    void beta3LocalSlotAndUpgradeCeilCallsMatchBatchInjection() throws Exception {
        ClassNode source = read(FORGE);
        int ceilCalls = 0;
        for (AbstractInsnNode instruction : method(source, "checkProcessing").instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("java/lang/Math") && call.name.equals("ceil")) ceilCalls++;
            }
        }
        assertEquals(4, ceilCalls);
        int stores = 0;
        for (AbstractInsnNode instruction : method(source, "distributeAntimatterToHatch").instructions.toArray()) {
            if (instruction instanceof org.objectweb.asm.tree.VarInsnNode) {
                org.objectweb.asm.tree.VarInsnNode var = (org.objectweb.asm.tree.VarInsnNode) instruction;
                if (var.getOpcode() == Opcodes.ISTORE && var.var == 11) {
                    assertEquals(Opcodes.D2I, var.getPrevious().getOpcode());
                    stores++;
                }
            }
        }
        assertEquals(1, stores);
    }

    @Test
    void pendingAntimatterAndPaidCooldownSurviveMovingTheController() {
        NBTTagCompound original = new NBTTagCompound();
        original.setLong("mbAntimatterOverflow", 1_234_567_890L);
        original.setLong("mbAntimatterRollingCost", 987_654_321L);
        original.setFloat("mbAntimatterMagnetic", -0.2F);
        NBTTagCompound batch = new NBTTagCompound();
        batch.setBoolean("antimatterCycle", true); batch.setInteger("progress", 7); batch.setInteger("maxProgress", 20);
        original.setTag("mbAntimatterBatch", batch);
        HostedMachineCoordinator coordinator = new HostedMachineCoordinator(null); coordinator.readFromNBT(original);
        NBTTagCompound dropped = new NBTTagCompound(); coordinator.writeMilestonesToItem(dropped);
        HostedMachineCoordinator placed = new HostedMachineCoordinator(null); placed.readFromNBT(dropped);
        NBTTagCompound saved = new NBTTagCompound(); placed.writeToNBT(saved);
        assertEquals(1_234_567_890L, saved.getLong("mbAntimatterOverflow"));
        assertEquals(987_654_321L, saved.getLong("mbAntimatterRollingCost"));
        assertEquals(-0.2F, saved.getFloat("mbAntimatterMagnetic"));
        assertEquals(7, saved.getCompoundTag("mbAntimatterBatch").getInteger("progress"));
        assertEquals(0L, saved.getCompoundTag("mbAntimatterBatch").getLong("energyUsage"));
    }

    private static long price(Object pricing, String name, long amount) throws Exception {
        Method method = pricing.getClass().getDeclaredMethod(name, long.class);
        method.setAccessible(true);
        return (Long) method.invoke(pricing, amount);
    }

    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** Execute the actual beta-3 pricing bytecode without booting the Minecraft renderer or fluid registries. */
    private Object pricingHarness() throws Exception {
        ClassNode source = read(FORGE);
        ClassNode pricing = new ClassNode();
        pricing.version = Opcodes.V1_8;
        pricing.access = Opcodes.ACC_PUBLIC;
        pricing.name = "com/gtnhblueprints/machine/NativeAntimatterPricingHarness";
        pricing.superName = "java/lang/Object";
        for (FieldNode field : source.fields) {
            if (Arrays.asList("rollingCost", "modifiers", "guiPassiveEnergy", "guiActiveEnergy").contains(field.name)) {
                field.access &= ~Opcodes.ACC_FINAL;
                pricing.fields.add(field);
            }
        }
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        pricing.methods.add(constructor);
        for (MethodNode method : source.methods) {
            if (!method.name.equals("calculateEnergyCost") && !method.name.equals("calculateEnergyContainmentCost")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    if (field.owner.equals(source.name)) field.owner = pricing.name;
                }
                if (instruction instanceof FrameNode) method.instructions.remove(instruction);
            }
            method.localVariables = null;
            pricing.methods.add(method);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        pricing.accept(writer);
        byte[] code = writer.toByteArray();
        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, code, 0, code.length); }
        }.define();
        Object instance = type.getDeclaredConstructor().newInstance();
        field(instance, "modifiers").set(instance, new float[4]);
        return instance;
    }

    private static boolean calls(MethodNode method, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals(name)) return true;
        }
        return false;
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().map(m -> (MethodNode) m).filter(m -> m.name.equals(name)).findFirst().get();
    }

    static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        java.net.URL resource = HostedAntimatterCompatibilityTest.class.getClassLoader().getResource(name + ".class");
        assertNotNull(resource, name);
        if (resource.openConnection() instanceof java.net.JarURLConnection) {
            java.net.JarURLConnection connection = (java.net.JarURLConnection) resource.openConnection();
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(new java.io.File(connection.getJarFileURL().toURI()));
                InputStream stream = zip.getInputStream(zip.getEntry(name + ".class"))) {
                new ClassReader(stream).accept(node, 0);
            }
        } else {
            try (InputStream stream = resource.openStream()) { new ClassReader(stream).accept(node, 0); }
        }
        return node;
    }
}

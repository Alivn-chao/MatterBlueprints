package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class HostedPlasmaForgeCompatibilityTest {

    @Test
    void twentyMinutesOfProductiveWorkEarnsDiscountAndSurvivesSave() {
        HostedMachineCoordinator.HostedJob job = new HostedMachineCoordinator.HostedJob();
        job.plasmaRuntime = HostedPlasmaForgeSupport.nativeRuntime(24000L, 576000L, false);
        assertEquals(24000L, job.plasmaRuntime);
        assertEquals(1D - 1D / 48D, HostedPlasmaForgeSupport.fuelMultiplier(job.plasmaRuntime), 1e-12);
        net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
        job.writeToNBT(saved);
        HostedMachineCoordinator.HostedJob restored = HostedMachineCoordinator.HostedJob.readFromNBT(saved);
        assertEquals(job.plasmaRuntime, restored.plasmaRuntime);
        restored.plasmaRuntime = Long.MAX_VALUE;
        restored.plasmaRuntime = HostedPlasmaForgeSupport.nativeRuntime(37840L, 576000L, false);
        assertEquals(37840L, restored.plasmaRuntime);
        assertEquals(0.9671527777777778D, HostedPlasmaForgeSupport.fuelMultiplier(restored.plasmaRuntime), 1e-12);
        assertEquals(576000L, HostedPlasmaForgeSupport.nativeRuntime(Long.MAX_VALUE, 576000L, false));
        assertEquals(288000L, HostedPlasmaForgeSupport.nativeRuntime(72000L, 144000L, false));
        assertEquals(576000L, HostedPlasmaForgeSupport.nativeRuntime(0L, 576000L, true));
    }

    @Test
    void nativeAdjustmentActuallyConsumesLessCatalystAtEachTimeAndIgnoresStaleWarmup() throws Exception {
        AssemblyTestBootstrap.initialize();
        java.lang.reflect.Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) singleton.get(null);
        // Execute the installed Java 8 pricing methods without Minecraft's renderer/world static initialization.
        Class<?> pricingClass = nativePricingClass();
        Object forge = unsafe.allocateInstance(pricingClass);
        java.lang.reflect.Field fuelsField = pricingClass.getDeclaredField("valid_fuels");
        fuelsField.setAccessible(true);
        net.minecraftforge.fluids.FluidStack[] fuels = new net.minecraftforge.fluids.FluidStack[1];
        fuelsField.set(null, fuels);
        net.minecraftforge.fluids.FluidStack originalFuel = fuels[0];
        java.lang.reflect.Field runtime = pricingClass.getDeclaredField("running_time");
        runtime.setAccessible(true);
        runtime.setLong(forge, 576000L);

        java.lang.reflect.Method adjust = pricingClass.getDeclaredMethod("recipeAfterAdjustments",
            gregtech.api.util.GTRecipe.class, net.minecraftforge.fluids.FluidStack[].class);
        adjust.setAccessible(true);
        try {
            fuels[0] = new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1);
            gregtech.api.util.GTRecipe recipe = (gregtech.api.util.GTRecipe) unsafe.allocateInstance(gregtech.api.util.GTRecipe.class);
            recipe.mInputs = new net.minecraft.item.ItemStack[0];
            recipe.mOutputs = new net.minecraft.item.ItemStack[0];
            recipe.mFluidInputs = new net.minecraftforge.fluids.FluidStack[] {
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 10000),
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.LAVA, 100) };
            recipe.mFluidOutputs = new net.minecraftforge.fluids.FluidStack[0];
            recipe.mInputChances = new int[0];
            recipe.mOutputChances = new int[0];
            recipe.mFluidInputChances = new int[] { 10000, 10000 };
            recipe.mFluidOutputChances = new int[0];
            long[] ticks = { 0L, 37840L, 288000L, 576000L };
            int[] expectedCosts = { 10000, 9672, 7500, 5000 };
            HostedMachineCoordinator.HostedJob job = new HostedMachineCoordinator.HostedJob();
            job.plasmaRuntime = Long.MAX_VALUE;
            for (int i = 0; i < ticks.length; i++) {
                net.minecraftforge.fluids.FluidStack[] stock = {
                    new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 20000),
                    new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.LAVA, 200) };
                {
                    job.plasmaRuntime = HostedPlasmaForgeSupport.nativeRuntime(ticks[i], 576000L, false);
                    runtime.setLong(forge, job.plasmaRuntime);

                    gregtech.api.util.GTRecipe adjusted = (gregtech.api.util.GTRecipe) adjust.invoke(forge, recipe, stock);
                    assertEquals(expectedCosts[i], adjusted.mFluidInputs[0].amount);
                    adjusted.consumeInput(1, stock, new net.minecraft.item.ItemStack[0]);
                    assertEquals(20000 - expectedCosts[i], stock[0].amount);
                    assertEquals(100, stock[1].amount);
                    assertEquals(10000, recipe.mFluidInputs[0].amount);
                }

                assertEquals(ticks[i], runtime.getLong(forge));
            }
        } finally {
            fuels[0] = originalFuel;
        }
    }

    @Test
    void installedGtVersionExposesExpectedFuelAndConfigurationFields() throws Exception {
        String name = "gregtech/common/tileentities/machines/multi/MTEPlasmaForge";
        ClassNode forge = read(name);
        assertField(forge, "running_time", "J");
        assertField(forge, "discount", "D");
        assertField(forge, "mHeatingCapacity", "I");
        assertField(forge, "convergence", "Z");
        assertField(forge, "catalystTypeForRecipesWithoutCatalyst", "I");
        assertEquals("gregtech/api/logic/ProcessingLogic", read(name + "$2").superName);
    }

    private Class<?> nativePricingClass() throws Exception {
        ClassNode source = read("gregtech/common/tileentities/machines/multi/MTEPlasmaForge");
        ClassNode pricing = new ClassNode();
        pricing.version = org.objectweb.asm.Opcodes.V1_8;
        pricing.access = org.objectweb.asm.Opcodes.ACC_PUBLIC;
        pricing.name = "com/gtnhblueprints/machine/NativeForgePricingHarness";
        pricing.superName = "java/lang/Object";
        for (Object entry : source.fields) {
            FieldNode field = (FieldNode) entry;
            if (java.util.Arrays.asList("running_time", "discount", "convergence", "extraCatalystNeeded",
                "catalystTypeForRecipesWithoutCatalyst", "enoughCatalyst", "valid_fuels").contains(field.name)) {
                field.access &= ~org.objectweb.asm.Opcodes.ACC_FINAL;
                pricing.fields.add(field);
            }
        }
        for (Object entry : source.methods) {
            org.objectweb.asm.tree.MethodNode method = (org.objectweb.asm.tree.MethodNode) entry;
            if (!method.name.equals("recipeAfterAdjustments") && !method.name.equals("recalculateDiscount")) continue;
            for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode) {
                    org.objectweb.asm.tree.FieldInsnNode field = (org.objectweb.asm.tree.FieldInsnNode) instruction;
                    if (field.owner.equals(source.name)) field.owner = pricing.name;
                }
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode) {
                    org.objectweb.asm.tree.MethodInsnNode call = (org.objectweb.asm.tree.MethodInsnNode) instruction;
                    if (call.owner.equals(source.name)) call.owner = pricing.name;
                    // GTRecipe.copy initializes mod-owner bookkeeping that requires the complete modpack.
                    // Substitute only the deep-copy fixture; pricing and consumeInput execute native bytecode.
                    if (call.owner.equals("gregtech/api/util/GTRecipe") && call.name.equals("copy")) {
                        call.setOpcode(org.objectweb.asm.Opcodes.INVOKESTATIC);
                        call.owner = "com/gtnhblueprints/machine/HostedPlasmaForgeCompatibilityTest";
                        call.name = "copyRecipeForPricing";
                        call.desc = "(Lgregtech/api/util/GTRecipe;)Lgregtech/api/util/GTRecipe;";
                    }
                }
                if (instruction instanceof org.objectweb.asm.tree.FrameNode) method.instructions.remove(instruction);
            }
            method.localVariables = null;
            pricing.methods.add(method);
        }
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
        pricing.accept(writer);
        byte[] code = writer.toByteArray();
        return new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, code, 0, code.length); }
        }.define();
    }

    public static gregtech.api.util.GTRecipe copyRecipeForPricing(gregtech.api.util.GTRecipe original) throws Exception {
        java.lang.reflect.Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        gregtech.api.util.GTRecipe copy = (gregtech.api.util.GTRecipe) ((sun.misc.Unsafe) singleton.get(null))
            .allocateInstance(gregtech.api.util.GTRecipe.class);
        for (java.lang.reflect.Field field : gregtech.api.util.GTRecipe.class.getFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) field.set(copy, field.get(original));
        }
        copy.mFluidInputs = new net.minecraftforge.fluids.FluidStack[original.mFluidInputs.length];
        for (int i = 0; i < copy.mFluidInputs.length; i++) copy.mFluidInputs[i] = original.mFluidInputs[i].copy();
        return copy;
    }

    private ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        java.net.JarURLConnection connection = (java.net.JarURLConnection) getClass().getClassLoader()
            .getResource(name + ".class").openConnection();
        // Verify the Java 8 entry deployed in Minecraft, not the JVM 17 multi-release variant used by the test JVM.
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(new java.io.File(connection.getJarFileURL().toURI()));
            java.io.InputStream stream = zip.getInputStream(zip.getEntry(name + ".class"))) {
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private void assertField(ClassNode node, String name, String descriptor) {
        assertTrue(node.fields.stream().map(field -> (FieldNode) field)
            .anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor)));
    }
}

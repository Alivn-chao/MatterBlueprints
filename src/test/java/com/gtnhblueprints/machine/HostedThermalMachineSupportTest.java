package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.AbstractInsnNode;

class HostedThermalMachineSupportTest {
    @Test
    void alloySmelterOverridesBaseDurationWithItsMachineSpeedField() throws Exception {
        ClassNode logic = read("gtPlusPlus/xmod/gregtech/common/tileentities/machines/multi/production/mega/MTEMegaAlloyBlastSmelter$1");
        boolean readsMachineSpeed = false;
        boolean overwritesDuration = false;
        for (Object entry : logic.methods) {
            MethodNode method = (MethodNode) entry;
            if (!method.name.equals("createOverclockCalculator")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                readsMachineSpeed |= call.name.endsWith("get$speedBonus");
                overwritesDuration |= call.name.equals("setDurationModifier");
            }
        }
        assertTrue(readsMachineSpeed);
        assertTrue(overwritesDuration);
    }
    @Test
    void preservesNativeGrowthUntilUnlockedAndLocksEachNativeMaximum() {
        assertEquals(1.3F, HostedThermalMachineSupport.boundedGrowth(1.3F, 1, false));
        assertEquals(1.3F, HostedThermalMachineSupport.boundedGrowth(1.3F, 2, false));
        assertEquals(2F, HostedThermalMachineSupport.boundedGrowth(1F, 1, true));
        assertEquals(1.5F, HostedThermalMachineSupport.boundedGrowth(1F, 2, true));
        assertEquals(1F, HostedThermalMachineSupport.boundedGrowth(Float.NaN, 1, false));
        assertEquals(1.5F, HostedThermalMachineSupport.boundedGrowth(2F, 2, false));
    }

    @Test
    void installedNativeMachinesExposeGrowthFluidAndStructureFields() throws Exception {
        ClassNode hearth = read("gregtech/common/tileentities/machines/multi/MTEExothermicHearth");
        assertField(hearth, "parallelModifier", "F");
        assertField(hearth, "runningTickCounter", "I");
        assertField(hearth, "isPyroSupplied", "Z");
        assertField(hearth, "heatingCapacity", "I");
        assertField(hearth, "glassTier", "I");
        ClassNode fridge = read("gregtech/common/tileentities/machines/multi/MTEEndothermicFridge");
        assertField(fridge, "speedBoost", "F");
        assertField(fridge, "runningTickCounter", "I");
        assertField(fridge, "isCryoEnabled", "Z");
        assertField(fridge, "machineTier", "I");
        assertField(fridge, "BOOSTER_FLUIDS", "Ljava/util/List;");
        assertField(fridge, "currentBoosterFluid", "Lgregtech/common/tileentities/machines/multi/MTEEndothermicFridge$BoosterFluid;");
        ClassNode alloy = read("gtPlusPlus/xmod/gregtech/common/tileentities/machines/multi/production/mega/MTEMegaAlloyBlastSmelter");
        assertField(alloy, "glassTier", "I");
        assertField(alloy, "speedBonus", "D");
        assertField(alloy, "coilType", "LgtPlusPlus/xmod/gregtech/common/tileentities/machines/multi/production/mega/MTEMegaAlloyBlastSmelter$CoilType;");
        assertField(hearth, "coilLevel", "Lgregtech/api/enums/HeatingCoilLevel;");
        assertField(alloy, "coilLevel", "Lgregtech/api/enums/HeatingCoilLevel;");
    }

    @Test
    void allThreeCalculatorsInheritHostOverclockSettings() throws Exception {
        String[] names = { "gregtech/common/tileentities/machines/multi/MTEExothermicHearth",
            "gregtech/common/tileentities/machines/multi/MTEEndothermicFridge",
            "gtPlusPlus/xmod/gregtech/common/tileentities/machines/multi/production/mega/MTEMegaAlloyBlastSmelter" };
        for (String name : names) {
            ClassNode logic = read(name + "$1");
            assertEquals("gregtech/api/logic/ProcessingLogic", logic.superName);
            boolean delegates = false;
            for (Object entry : logic.methods) {
                MethodNode method = (MethodNode) entry;
                if (!method.name.equals("createOverclockCalculator")) continue;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.owner.equals("gregtech/api/logic/ProcessingLogic") && call.name.equals("createOverclockCalculator")) delegates = true;
                    assertNotEquals("setDurationDecreasePerOC", call.name);
                    assertNotEquals("setEUtIncreasePerOC", call.name);
                }
            }
            assertTrue(delegates, name);
        }
    }

    private ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        java.net.JarURLConnection connection = (java.net.JarURLConnection) getClass().getClassLoader()
            .getResource(name + ".class").openConnection();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(new java.io.File(connection.getJarFileURL().toURI()));
            java.io.InputStream stream = zip.getInputStream(zip.getEntry(name + ".class"))) {
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private void assertField(ClassNode node, String name, String descriptor) {
        assertTrue(node.fields.stream().map(field -> (FieldNode) field)
            .anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor)), node.name + "." + name);
    }
}

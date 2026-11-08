package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
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

import gregtech.api.util.OverclockCalculator;

/** Contracts against the installed GT jar, without initializing machine structures or a game world. */
class HostedNativeOverclockAuditTest {
    private static final String NANO = "gregtech/common/tileentities/machines/multi/MTENanoForge";

    @Test
    void nanoforgeFoldsAllHatchPowerIntoVoltageBeforeSettingOneAmp() throws Exception {
        MethodNode power = method(NANO, "setProcessingLogicPower");
        assertTrue(calls(power, "getMaxInputEu"));
        assertTrue(calls(power, "setAvailableVoltage"));
        assertTrue(callsWithConstant(power, "setAvailableAmperage", Opcodes.LCONST_1));
        assertTrue(callsWithConstant(power, "setAmperageOC", Opcodes.ICONST_0));
        assertTrue(calls(power, "setUnlimitedTierSkips"));
        assertTrue(calls(method("gregtech/api/metatileentity/implementations/MTEExtendedPowerMultiBlockBase",
            "getMaxInputEu"), "getTotalEuMulti"));
    }

    @Test
    void nativeFoldedPowerModelStillOverclocksAtUmv65536Amps() {
        long voltage = 134_217_728L;
        int recipeEUt = 125_829_120;
        OverclockCalculator low = new OverclockCalculator().setRecipeEUt(recipeEUt).setDuration(480_000)
            .setEUt(voltage).setAmperage(1).setAmperageOC(false).calculate();
        OverclockCalculator high = new OverclockCalculator().setRecipeEUt(recipeEUt).setDuration(480_000)
            .setEUt(voltage * 65_536).setAmperage(1).setAmperageOC(false).calculate();
        assertEquals(0, low.getPerformedOverclocks());
        assertEquals(8, high.getPerformedOverclocks());
        assertTrue(high.getDuration() < low.getDuration());
        assertTrue(high.getConsumption() > low.getConsumption());
        assertTrue(high.getConsumption() <= voltage * 65_536);
    }

    @Test
    void ordinaryProcessingUsesAmperageOcWhileFoldedPowerMachinesKeepTheirNativeModel() throws Exception {
        assertTrue(callsWithConstant(method("gregtech/api/metatileentity/implementations/MTEMultiBlockBase",
            "setProcessingLogicPower"), "setAmperageOC", Opcodes.ICONST_1));
        String[] folded = {
            NANO,
            "gregtech/common/tileentities/machines/multi/MTEExothermicHearth",
            "gregtech/common/tileentities/machines/multi/MTEEndothermicFridge",
            "gregtech/common/tileentities/machines/multi/MTEMegaDistillationTower",
            "gregtech/common/tileentities/machines/multi/MTEMegaOilCracker",
            "goodgenerator/blocks/tileEntity/MTEComponentAssemblyLine",
            "gtPlusPlus/xmod/gregtech/common/tileentities/machines/multi/production/mega/MTEMegaAlloyBlastSmelter"
        };
        for (String machine : folded) {
            MethodNode power = method(machine, "setProcessingLogicPower");
            assertTrue(calls(power, "getMaxInputEu"), machine);
            assertTrue(callsWithConstant(power, "setAvailableAmperage", Opcodes.LCONST_1), machine);
        }
    }

    @Test
    void auditSeparatelyIdentifiesNativeNanoforgeOverridesOfHostSpeedAndParallel() throws Exception {
        MethodNode validate = method(NANO + "$1", "validateRecipe");
        boolean overwritesParallel = false;
        for (AbstractInsnNode instruction : validate.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                overwritesParallel |= field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("maxParallel");
            }
        }
        assertTrue(overwritesParallel);
        String[] overrides = { NANO + "$1", "goodgenerator/blocks/tileEntity/MTEPreciseAssembler$1",
            "goodgenerator/blocks/tileEntity/MTEComponentAssemblyLine$1",
            "gregtech/common/tileentities/machines/multi/pcb/MTEPCBFactory$1" };
        for (String logic : overrides) {
            assertTrue(calls(method(logic, "createOverclockCalculator"), "setDurationModifier"), logic);
        }
    }

    private static boolean calls(MethodNode method, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals(name)) return true;
        }
        return false;
    }

    private static boolean callsWithConstant(MethodNode method, String name, int opcode) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode) || !((MethodInsnNode) instruction).name.equals(name)) continue;
            AbstractInsnNode previous = instruction.getPrevious();
            while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
            if (previous != null && previous.getOpcode() == opcode) return true;
        }
        return false;
    }

    private static MethodNode method(String owner, String name) throws Exception {
        ClassNode node = new ClassNode();
        JarURLConnection connection = (JarURLConnection) HostedNativeOverclockAuditTest.class.getClassLoader()
            .getResource(owner + ".class").openConnection();
        try (ZipFile zip = new ZipFile(new java.io.File(connection.getJarFileURL().toURI()));
            InputStream stream = zip.getInputStream(zip.getEntry(owner + ".class"))) {
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (method.name.equals(name)) return method;
        }
        throw new AssertionError(owner + "." + name);
    }
}

package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class HostedMilestoneItemTest {
    @Test
    void droppedControllerRestoresEveryMilestoneAndContinuesCounting() {
        HostedMachineCoordinator original = new HostedMachineCoordinator(null);
        HostedProgression progression = original.getProgression();
        NBTTagCompound stored = new NBTTagCompound();
        stored.setLong("productiveTicks", 12096000L);
        stored.setLong("workUnits", 10000000L);
        stored.setByteArray("energyThroughput", BigInteger.TEN.pow(30).toByteArray());
        stored.setLong("generatorWorkRemainder", 19);
        stored.setBoolean("plasmaFuelUnlocked", true);
        progression.readFromNBT(stored);

        NBTTagCompound dropped = new NBTTagCompound();
        dropped.setByte("mColor", (byte) 7);
        original.writeMilestonesToItem(dropped);
        HostedMachineCoordinator placed = new HostedMachineCoordinator(null);
        placed.readFromNBT((NBTTagCompound) dropped.copy());
        HostedProgression restored = placed.getProgression();
        assertEquals(12096000L, restored.getProductiveTicks());
        assertEquals(10000000L, restored.getWorkUnits());
        assertEquals(BigInteger.TEN.pow(30), restored.getEnergyThroughput());
        assertEquals(6, restored.getRuntimeLevel());
        assertEquals(6, restored.getWorkLevel());
        assertEquals(6, restored.getEnergyLevel());
        assertTrue(restored.hasPermanentPlasmaFuelDiscount());
        assertEquals(7, dropped.getByte("mColor"));
        restored.recordGeneratorTick(100, 1);
        assertEquals(10000001L, restored.getWorkUnits());
        assertEquals(12096001L, restored.getProductiveTicks());
        assertEquals(BigInteger.TEN.pow(30).add(BigInteger.valueOf(100)), restored.getEnergyThroughput());
    }

    @Test
    void permanentUnlockSurvivesMovingEvenWhenItsCounterIsBelowTheCurrentThreshold() {
        HostedMachineCoordinator coordinator = new HostedMachineCoordinator(null);
        NBTTagCompound low = new NBTTagCompound();
        low.setLong("productiveTicks", 1);
        low.setBoolean("plasmaFuelUnlocked", true);
        coordinator.getProgression().readFromNBT(low);
        NBTTagCompound item = new NBTTagCompound();
        coordinator.writeMilestonesToItem(item);
        HostedMachineCoordinator placed = new HostedMachineCoordinator(null);
        placed.readFromNBT(item);
        assertEquals(1, placed.getProgression().getProductiveTicks());
        assertTrue(placed.getProgression().hasPermanentPlasmaFuelDiscount());
        assertEquals(576000L, placed.getProgression().getPlasmaForgeRuntime());
    }

    @Test
    void movingDoesNotPutWirelessCreditOrRunningRecipeClaimsIntoTheItem() {
        HostedMachineCoordinator original = new HostedMachineCoordinator(null);
        NBTTagCompound dropped = new NBTTagCompound();
        original.writeMilestonesToItem(dropped);
        assertTrue(dropped.hasKey("mbHostProgression"));
        assertFalse(dropped.hasKey("mbHostClaims"));
        assertFalse(dropped.hasKey("mbHostBindings"));
        assertFalse(dropped.hasKey("mbHostWirelessEU"));
        assertFalse(dropped.hasKey("mbHostWirelessAccount"));
        HostedMachineCoordinator fresh = new HostedMachineCoordinator(null);
        fresh.readFromNBT(new NBTTagCompound());
        assertEquals(0, fresh.getProgression().getProductiveTicks());
        assertEquals(BigInteger.ZERO, fresh.getProgression().getEnergyThroughput());
    }

    @Test
    void controllerOverridesTheNativeItemDropHook() throws Exception {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        try (java.io.InputStream input = getClass().getResourceAsStream("/com/gtnhblueprints/machine/MTEHostedMachineController.class")) {
            new org.objectweb.asm.ClassReader(input).accept(node, 0);
        }
        org.objectweb.asm.tree.MethodNode hook = node.methods.stream()
            .map(method -> (org.objectweb.asm.tree.MethodNode) method)
            .filter(method -> method.name.equals("setItemNBT") && method.desc.equals("(Lnet/minecraft/nbt/NBTTagCompound;)V"))
            .findFirst().orElseThrow(AssertionError::new);
        boolean exports = false;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : hook.instructions.toArray()) {
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode) {
                org.objectweb.asm.tree.MethodInsnNode call = (org.objectweb.asm.tree.MethodInsnNode) instruction;
                if (call.name.equals("writeMilestonesToItem")) exports = true;
            }
        }
        assertTrue(exports);
    }
}

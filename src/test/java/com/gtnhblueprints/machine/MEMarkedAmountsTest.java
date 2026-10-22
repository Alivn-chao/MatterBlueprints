package com.gtnhblueprints.machine;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import gregtech.common.tileentities.machines.MTEHatchInputME;
class MEMarkedAmountsTest {
    @org.junit.jupiter.api.BeforeAll static void initialize() throws Exception { AssemblyTestBootstrap.initialize(); }
    @Test void unsetMarksKeepTheOriginalTwoPointOneGAvailability() {
        MEMarkedAmounts limits = new MEMarkedAmounts();
        for (int i = 0; i < 16; i++) assertEquals(Integer.MAX_VALUE, limits.get(i));
        assertEquals(Integer.MAX_VALUE, limits.cap(0, Integer.MAX_VALUE));
        assertEquals(123, limits.cap(0, 123));
    }
    @Test void theMarkedAmountCapsBothTheVirtualViewAndItsNativeDebitBaseline() {
        MEMarkedAmounts limits = new MEMarkedAmounts(); limits.set(0, 256);
        FluidStack network = new FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 10000);
        MTEHatchInputME.Slot slot = new MTEHatchInputME.Slot(network.copy());
        slot.extracted = network.copy(); slot.extractedAmount = 10000;
        limits.apply(0, slot);
        assertEquals(256, slot.extracted.amount);
        assertEquals(256, slot.extractedAmount);
        slot.extracted.amount -= 64;
        assertEquals(64, slot.extractedAmount - slot.extracted.amount);
        assertEquals(10000, network.amount);
    }
    @Test void aSmallerAEInventoryIsNeverInflatedToTheMarkedQuantity() {
        MEMarkedAmounts limits = new MEMarkedAmounts(); limits.set(0, 1000);
        MTEHatchInputME.Slot slot = new MTEHatchInputME.Slot(new FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1));
        slot.extracted = new FluidStack(slot.config, 21); slot.extractedAmount = 21;
        limits.apply(0, slot);
        assertEquals(21, slot.extracted.amount);
        assertEquals(21, slot.extractedAmount);
        limits.set(0, 0); limits.apply(0, slot);
        assertNull(slot.extracted); assertEquals(0, slot.extractedAmount);
    }
    @Test void marksPersistAndMissingOldValuesUseTheNativeDefault() {
        MEMarkedAmounts original = new MEMarkedAmounts(); original.set(3, 256); original.set(15, 0);
        NBTTagCompound saved = new NBTTagCompound(); original.save(saved);
        MEMarkedAmounts restored = new MEMarkedAmounts(); restored.load(saved);
        assertEquals(256, restored.get(3)); assertEquals(0, restored.get(15));
        assertEquals(Integer.MAX_VALUE, restored.get(1));
        restored.load(new NBTTagCompound()); assertEquals(Integer.MAX_VALUE, restored.get(3));
    }
    @Test void itemQuantityLimitsKeepTheNativeConsumptionDeltaInsteadOfChargingTheWholeMark() throws Exception {
        MEMarkedAmounts limits = new MEMarkedAmounts(); limits.set(0, 256);
        net.minecraft.item.ItemStack network = new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_ingot, 10000);
        java.lang.reflect.Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        // The native cached AE prototype needs a running AE lifecycle; the limit only changes these view fields.
        gregtech.common.tileentities.machines.MTEHatchInputBusME.Slot slot =
            (gregtech.common.tileentities.machines.MTEHatchInputBusME.Slot) ((sun.misc.Unsafe) singleton.get(null))
                .allocateInstance(gregtech.common.tileentities.machines.MTEHatchInputBusME.Slot.class);
        slot.extracted = network.copy(); slot.extractedAmount = 10000;
        limits.apply(0, slot);
        assertEquals(256, slot.extracted.stackSize);
        assertEquals(256, slot.extractedAmount);
        slot.extracted.stackSize -= 3;
        assertEquals(3, slot.extractedAmount - slot.extracted.stackSize);
        assertEquals(10000, network.stackSize);
    }
}

package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class HostedYieldAccumulatorTest {

    private static final Item TEST_OUTPUT = new Item().setUnlocalizedName("hostedYieldTestOutput");

    @Test
    void quarterBonusAccumulatesDeterministically() {
        HostedYieldAccumulator accumulator = new HostedYieldAccumulator();
        int produced = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack[] output = { new ItemStack(TEST_OUTPUT) };
            accumulator.apply(output, null, 1);
            produced += output[0].stackSize;
        }
        assertEquals(5, produced);
    }

    @Test
    void remainderSurvivesSaveAndLoad() {
        HostedYieldAccumulator first = new HostedYieldAccumulator();
        ItemStack[] firstOutput = { new ItemStack(TEST_OUTPUT) };
        first.apply(firstOutput, null, 1);
        NBTTagCompound tag = new NBTTagCompound();
        first.writeToNBT(tag);

        HostedYieldAccumulator restored = new HostedYieldAccumulator();
        restored.readFromNBT(tag);
        int produced = firstOutput[0].stackSize;
        for (int i = 0; i < 3; i++) {
            ItemStack[] output = { new ItemStack(TEST_OUTPUT) };
            restored.apply(output, null, 1);
            produced += output[0].stackSize;
        }
        assertEquals(5, produced);
    }
}

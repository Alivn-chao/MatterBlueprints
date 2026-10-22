package com.gtnhblueprints.machine;

import java.util.Arrays;
import net.minecraft.nbt.NBTTagCompound;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/** A visibility limit on native virtual ME slots. This class never owns or transfers resources. */
final class MEMarkedAmounts {
    static final int SLOTS = 16;
    private final int[] amounts = new int[SLOTS];
    MEMarkedAmounts() { Arrays.fill(amounts, Integer.MAX_VALUE); }
    int get(int index) { return amounts[index]; }
    void set(int index, int amount) { amounts[index] = Math.max(0, amount); }
    int cap(int index, int available) { return Math.min(Math.max(0, available), amounts[index]); }
    void apply(int index, MTEHatchInputBusME.Slot slot) {
        if (slot == null) return;
        slot.extractedAmount = cap(index, slot.extracted == null ? 0 : slot.extracted.stackSize);
        if (slot.extractedAmount == 0) slot.extracted = null;
        else slot.extracted.stackSize = slot.extractedAmount;
    }
    void apply(int index, MTEHatchInputME.Slot slot) {
        if (slot == null) return;
        slot.extractedAmount = cap(index, slot.extracted == null ? 0 : slot.extracted.amount);
        if (slot.extractedAmount == 0) slot.extracted = null;
        else slot.extracted.amount = slot.extractedAmount;
    }
    void save(NBTTagCompound tag) { tag.setIntArray("mbMarkedAmounts", amounts); }
    void load(NBTTagCompound tag) {
        Arrays.fill(amounts, Integer.MAX_VALUE);
        int[] saved = tag.getIntArray("mbMarkedAmounts");
        for (int i = 0; i < Math.min(saved.length, SLOTS); i++) set(i, saved[i]);
    }
}

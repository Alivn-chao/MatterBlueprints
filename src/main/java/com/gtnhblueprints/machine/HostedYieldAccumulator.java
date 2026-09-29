package com.gtnhblueprints.machine;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

/** Deterministic quarter-unit output bonuses with conservative exclusions for reusable or configured objects. */
final class HostedYieldAccumulator {

    private final Map<String, Integer> remainders = new HashMap<String, Integer>();

    void apply(ItemStack[] items, FluidStack[] fluids, int bonusQuarters) {
        if (bonusQuarters <= 0) return;
        if (items != null) {
            for (ItemStack stack : items) {
                if (!isSafe(stack)) continue;
                stack.stackSize = addBonus(itemKey(stack), stack.stackSize, bonusQuarters);
            }
        }
        if (fluids != null) {
            for (FluidStack stack : fluids) {
                if (stack == null || stack.amount <= 0 || stack.getFluid() == null) continue;
                stack.amount = addBonus("f:" + stack.getFluid().getName(), stack.amount, bonusQuarters);
            }
        }
    }

    void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Integer> entry : remainders.entrySet()) {
            if (entry.getValue() <= 0) continue;
            NBTTagCompound value = new NBTTagCompound();
            value.setString("key", entry.getKey());
            value.setByte("quarter", (byte) (entry.getValue() & 3));
            list.appendTag(value);
        }
        tag.setTag("remainders", list);
    }

    void readFromNBT(NBTTagCompound tag) {
        remainders.clear();
        NBTTagList list = tag.getTagList("remainders", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound value = list.getCompoundTagAt(i);
            String key = value.getString("key");
            int remainder = value.getByte("quarter") & 3;
            if (!key.isEmpty() && remainder > 0) remainders.put(key, remainder);
        }
    }

    private int addBonus(String key, int amount, int bonusQuarters) {
        if (amount <= 0) return amount;
        long numerator = (long) amount * bonusQuarters + remainders.getOrDefault(key, 0);
        long bonus = numerator / 4L;
        int remainder = (int) (numerator % 4L);
        if (remainder == 0) remainders.remove(key);
        else remainders.put(key, remainder);
        return (int) Math.min(Integer.MAX_VALUE, amount + bonus);
    }

    private static boolean isSafe(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || stack.hasTagCompound()) return false;
        Item item = stack.getItem();
        if (item == null || item.isDamageable()) return false;
        String name = item.getUnlocalizedName(stack).toLowerCase(java.util.Locale.ROOT);
        return !name.contains("catalyst") && !name.contains("mold") && !name.contains("shape")
            && !name.contains("tool") && !name.contains("data") && !name.contains("orb")
            && !name.contains("container") && !name.contains("cell.empty");
    }

    private static String itemKey(ItemStack stack) {
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return "i:" + String.valueOf(name) + ":" + stack.getItemDamage();
    }
}

package com.gtnhblueprints.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public final class TileBlueprintConfigurator extends TileEntity implements IInventory {

    private ItemStack replacement;

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? replacement : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot != 0 || replacement == null) return null;
        if (replacement.stackSize <= amount) {
            ItemStack result = replacement;
            replacement = null;
            markDirty();
            return result;
        }
        ItemStack result = replacement.splitStack(amount);
        if (replacement.stackSize <= 0) replacement = null;
        markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot != 0) return null;
        ItemStack result = replacement;
        replacement = null;
        return result;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) return;
        replacement = stack;
        if (replacement != null && replacement.stackSize > getInventoryStackLimit()) {
            replacement.stackSize = getInventoryStackLimit();
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.matterblueprints.configurator";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && stack != null;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        replacement = tag.hasKey("Replacement", 10) ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Replacement")) : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (replacement != null) {
            NBTTagCompound item = new NBTTagCompound();
            replacement.writeToNBT(item);
            tag.setTag("Replacement", item);
        }
    }
}

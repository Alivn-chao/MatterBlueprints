package com.gtnhblueprints.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.gtnhblueprints.tile.TileBlueprintConfigurator;

public final class ContainerBlueprintConfigurator extends Container {

    public final TileBlueprintConfigurator configurator;

    public ContainerBlueprintConfigurator(InventoryPlayer playerInventory, TileBlueprintConfigurator configurator) {
        this.configurator = configurator;
        addSlotToContainer(new Slot(configurator, 0, 360, 124) {

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        });

        int inventoryLeft = 114;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(
                    new Slot(playerInventory, column + row * 9 + 9, inventoryLeft + column * 18, 179 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlotToContainer(new Slot(playerInventory, column, inventoryLeft + column * 18, 237));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return configurator.isUseableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack result = null;
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        result = stack.copy();
        if (index == 0) {
            if (!mergeItemStack(stack, 1, inventorySlots.size(), true)) return null;
        } else {
            ItemStack one = stack.copy();
            one.stackSize = 1;
            if (!mergeItemStack(one, 0, 1, false)) return null;
            stack.stackSize--;
        }
        if (stack.stackSize <= 0) slot.putStack(null);
        else slot.onSlotChanged();
        return result;
    }
}

package com.gtnhblueprints.model;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnhblueprints.MatterBlueprints;
import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.IBlockApplyContext;
import com.recursive_pineapple.matter_manipulator.common.building.IPseudoInventory;
import com.recursive_pineapple.matter_manipulator.common.building.ITileAnalysisIntegration;
import com.recursive_pineapple.matter_manipulator.common.building.PortableItemStack;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Transform;
import com.recursive_pineapple.matter_manipulator.common.utils.BigItemStack;

/** Copies the six face attachments and persistent settings used by Translocator 1.4.4. */
public final class TranslocatorAnalysisResult implements ITileAnalysisIntegration {

    private static final String ITEM_TILE = "codechicken.translocator.TileItemTranslocator";
    private static final String LIQUID_TILE = "codechicken.translocator.TileLiquidTranslocator";

    public NBTTagCompound state;
    public PortableItemStack baseItem;
    public PortableItemStack[] extraDrops;

    public static boolean isTranslocator(TileEntity tile) {
        if (tile == null) return false;
        String name = tile.getClass().getName();
        return ITEM_TILE.equals(name) || LIQUID_TILE.equals(name);
    }

    public static TranslocatorAnalysisResult analyze(TileEntity tile, ItemStack baseStack) {
        if (!isTranslocator(tile) || baseStack == null) return null;
        try {
            TranslocatorAnalysisResult result = new TranslocatorAnalysisResult();
            result.state = new NBTTagCompound();
            tile.writeToNBT(result.state);
            result.state.removeTag("x");
            result.state.removeTag("y");
            result.state.removeTag("z");
            result.baseItem = PortableItemStack.withNBT(baseStack.copy());
            result.extraDrops = withoutOneBase(captureDrops(tile), baseStack);
            return result;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MatterBlueprints.LOG.error("Could not capture Translocator state", exception);
            return null;
        }
    }

    @Override
    public boolean apply(IBlockApplyContext context) {
        TileEntity tile = context.getTileEntity();
        if (!isTranslocator(tile) || state == null) {
            context.error(new ChatComponentText("无法恢复 Translocator：目标方块实体不匹配"));
            return false;
        }
        try {
            clearAttachments(tile);
            NBTTagCompound restored = (NBTTagCompound) state.copy();
            restored.setInteger("x", context.getX());
            restored.setInteger("y", context.getY());
            restored.setInteger("z", context.getZ());
            tile.readFromNBT(restored);
            tile.markDirty();
            World world = context.getWorld();
            world.markBlockForUpdate(context.getX(), context.getY(), context.getZ());
            Block block = world.getBlock(context.getX(), context.getY(), context.getZ());
            world.notifyBlocksOfNeighborChange(context.getX(), context.getY(), context.getZ(), block);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MatterBlueprints.LOG.error("Could not restore Translocator state", exception);
            context.error(new ChatComponentText("恢复 Translocator 配置失败: " + exception.getMessage()));
            return false;
        }
    }

    @Override
    public boolean getRequiredItemsForExistingBlock(IBlockApplyContext context) {
        TileEntity existing = context.getTileEntity();
        if (isTranslocator(existing) && baseItem != null) {
            try {
                ItemStack base = baseItem.toStack();
                for (PortableItemStack drop : withoutOneBase(captureDrops(existing), base)) {
                    if (drop != null && drop.toStack() != null) context.givePlayerItems(drop.toStack());
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                MatterBlueprints.LOG.warn("Could not inspect existing Translocator drops", exception);
            }
        }
        return consumeExtraDrops(context);
    }

    @Override
    public boolean getRequiredItemsForNewBlock(IBlockApplyContext context) {
        return consumeExtraDrops(context);
    }

    @Override
    public void getItemTag(ItemStack stack) {}

    @Override
    public void getItemDetailsChat(List<IChatComponent> details) {
        details.add(new ChatComponentText("Translocator 附件 " + attachmentCount() + " 面"));
    }

    @Override
    public void transform(Transform transform) {
        if (state == null) return;
        NBTTagCompound transformed = (NBTTagCompound) state.copy();
        for (int side = 0; side < 6; side++) transformed.removeTag("atmt" + side);
        for (int side = 0; side < 6; side++) {
            String key = "atmt" + side;
            if (!state.hasKey(key)) continue;
            ForgeDirection target = transform.apply(ForgeDirection.getOrientation(side));
            if (target == null || target == ForgeDirection.UNKNOWN) continue;
            transformed.setTag("atmt" + target.ordinal(), state.getCompoundTag(key).copy());
        }
        state = transformed;
    }

    @Override
    public TranslocatorAnalysisResult clone() {
        TranslocatorAnalysisResult copy = new TranslocatorAnalysisResult();
        copy.state = state == null ? null : (NBTTagCompound) state.copy();
        copy.baseItem = baseItem == null ? null : baseItem.clone();
        if (extraDrops != null) {
            copy.extraDrops = new PortableItemStack[extraDrops.length];
            for (int i = 0; i < extraDrops.length; i++) {
                copy.extraDrops[i] = extraDrops[i] == null ? null : extraDrops[i].clone();
            }
        }
        return copy;
    }

    @Override
    public void migrate() {}

    private boolean consumeExtraDrops(IBlockApplyContext context) {
        if (extraDrops == null) return true;
        List<ItemStack> consumed = new ArrayList<>();
        for (PortableItemStack portable : extraDrops) {
            if (portable == null) continue;
            ItemStack stack = portable.toStack();
            if (stack == null) continue;
            boolean found = context.tryConsumeItems(
                Collections.singletonList(BigItemStack.create(stack)),
                IPseudoInventory.CONSUME_FUZZY).leftBoolean();
            if (found) {
                consumed.add(stack);
                continue;
            }
            for (ItemStack refund : consumed) context.givePlayerItems(refund);
            context.warn(new ChatComponentText("缺少 Translocator 配件: " + stack.getDisplayName()));
            return false;
        }
        return true;
    }

    private int attachmentCount() {
        if (state == null) return 0;
        int count = 0;
        for (int side = 0; side < 6; side++) if (state.hasKey("atmt" + side)) count++;
        return count;
    }

    private static List<ItemStack> captureDrops(TileEntity tile) throws ReflectiveOperationException {
        Object[] attachments = attachments(tile);
        List<ItemStack> drops = new ArrayList<>();
        for (Object attachment : attachments) {
            if (attachment == null) continue;
            Method getDrops = attachment.getClass().getMethod("getDrops");
            Object value = getDrops.invoke(attachment);
            if (!(value instanceof Iterable<?>)) continue;
            for (Object drop : (Iterable<?>) value) {
                if (drop instanceof ItemStack) drops.add(((ItemStack) drop).copy());
            }
        }
        return drops;
    }

    private static PortableItemStack[] withoutOneBase(List<ItemStack> drops, ItemStack baseStack) {
        List<PortableItemStack> extras = new ArrayList<>();
        boolean removedBase = false;
        for (ItemStack original : drops) {
            if (original == null || original.getItem() == null) continue;
            ItemStack drop = original.copy();
            if (!removedBase && sameItem(drop, baseStack)) {
                removedBase = true;
                drop.stackSize--;
            }
            if (drop.stackSize > 0) extras.add(PortableItemStack.withNBT(drop));
        }
        return extras.toArray(new PortableItemStack[extras.size()]);
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return right != null
            && left.getItem() == right.getItem()
            && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static Object[] attachments(TileEntity tile) throws ReflectiveOperationException {
        Field field = findField(tile.getClass(), "attachments");
        Object value = field.get(tile);
        if (!(value instanceof Object[])) throw new IllegalStateException("Translocator attachments field is not an array");
        return (Object[]) value;
    }

    private static void clearAttachments(TileEntity tile) throws ReflectiveOperationException {
        Arrays.fill(attachments(tile), null);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}

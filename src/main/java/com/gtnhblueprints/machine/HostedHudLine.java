package com.gtnhblueprints.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

/** Sends translation keys and typed arguments, never server-localized HUD text. */
final class HostedHudLine {
    private final NBTTagCompound data;

    private HostedHudLine(NBTTagCompound data) { this.data = data; }

    static HostedHudLine text(String key, Object... args) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("key", key);
        NBTTagList values = new NBTTagList();
        for (Object arg : args) {
            NBTTagCompound value = new NBTTagCompound();
            if (arg instanceof HostedHudLine) value.setTag("line", ((HostedHudLine) arg).data);
            else if (arg instanceof ItemStack) value.setTag("item", ((ItemStack) arg).writeToNBT(new NBTTagCompound()));
            else if (arg instanceof FluidStack) value.setTag("fluid", ((FluidStack) arg).writeToNBT(new NBTTagCompound()));
            else if (arg instanceof Float || arg instanceof Double) value.setDouble("decimal", ((Number) arg).doubleValue());
            else if (arg instanceof Number) value.setLong("integer", ((Number) arg).longValue());
            else value.setString("text", String.valueOf(arg));
            values.appendTag(value);
        }
        data.setTag("args", values);
        return new HostedHudLine(data);
    }

    static HostedHudLine read(NBTTagCompound data) { return new HostedHudLine(data); }

    String render() {
        NBTTagList values = data.getTagList("args", 10);
        Object[] args = new Object[values.tagCount()];
        for (int i = 0; i < args.length; i++) {
            NBTTagCompound value = values.getCompoundTagAt(i);
            if (value.hasKey("line")) args[i] = read(value.getCompoundTag("line")).render();
            else if (value.hasKey("item")) {
                ItemStack item = ItemStack.loadItemStackFromNBT(value.getCompoundTag("item"));
                args[i] = item == null ? "?" : item.getDisplayName();
            } else if (value.hasKey("fluid")) {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(value.getCompoundTag("fluid"));
                args[i] = fluid == null ? "?" : fluid.getLocalizedName();
            } else if (value.hasKey("decimal")) args[i] = value.getDouble("decimal");
            else if (value.hasKey("integer")) args[i] = value.getLong("integer");
            else args[i] = value.getString("text");
        }
        return StatCollector.translateToLocalFormatted(data.getString("key"), args);
    }

    static NBTTagList write(List<HostedHudLine> lines) {
        NBTTagList result = new NBTTagList();
        for (HostedHudLine line : lines) result.appendTag(line.data);
        return result;
    }

    static List<String> render(List<HostedHudLine> lines) {
        List<String> result = new ArrayList<String>();
        for (HostedHudLine line : lines) result.add(line.render());
        return result;
    }
}

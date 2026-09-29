package com.gtnhblueprints.io;

import java.lang.reflect.Type;

import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/** Serializes Forge fluids through their stable NBT representation instead of reflecting into the fluid registry. */
public final class FluidStackJsonAdapter implements JsonSerializer<FluidStack>, JsonDeserializer<FluidStack> {

    @Override
    public JsonElement serialize(FluidStack source, Type type, JsonSerializationContext context) {
        if (source == null) return null;
        return context.serialize(source.writeToNBT(new NBTTagCompound()), NBTTagCompound.class);
    }

    @Override
    public FluidStack deserialize(JsonElement json, Type type, JsonDeserializationContext context)
        throws JsonParseException {
        if (json == null || json.isJsonNull()) return null;
        NBTTagCompound tag = context.deserialize(json, NBTTagCompound.class);
        FluidStack stack = FluidStack.loadFluidStackFromNBT(tag);
        if (stack == null) throw new JsonParseException("蓝图中的流体不存在于当前整合包");
        return stack;
    }
}

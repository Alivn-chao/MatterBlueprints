package com.gtnhblueprints.io;

import java.lang.reflect.Type;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils;

/** Matter Manipulator 0.1.46 writes an empty NBT string as {@code "s"}, but refuses to read it back. */
public final class CompatibleNBTJsonAdapter
    implements JsonSerializer<NBTTagCompound>, JsonDeserializer<NBTTagCompound> {

    @Override
    public JsonElement serialize(NBTTagCompound source, Type type, JsonSerializationContext context) {
        return MMUtils.toJsonObjectExact(source);
    }

    @Override
    public NBTTagCompound deserialize(JsonElement json, Type type, JsonDeserializationContext context)
        throws JsonParseException {
        NBTBase decoded = decode(json);
        if (!(decoded instanceof NBTTagCompound)) throw new JsonParseException("expected an NBT compound");
        return (NBTTagCompound) decoded;
    }

    private static NBTBase decode(JsonElement json) {
        if (json == null || json.isJsonNull()) return null;
        if (json.isJsonPrimitive()) {
            if (json.getAsJsonPrimitive().isString() && "s".equals(json.getAsString())) {
                return new NBTTagString("");
            }
            return MMUtils.toNbtExact(json);
        }
        if (json.isJsonArray()) {
            NBTTagList list = new NBTTagList();
            JsonArray array = json.getAsJsonArray();
            for (JsonElement element : array) list.appendTag(decode(element));
            return list;
        }
        if (json.isJsonObject()) {
            NBTTagCompound compound = new NBTTagCompound();
            JsonObject object = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                compound.setTag(entry.getKey(), decode(entry.getValue()));
            }
            return compound;
        }
        throw new JsonParseException("unhandled NBT JSON element: " + json);
    }
}

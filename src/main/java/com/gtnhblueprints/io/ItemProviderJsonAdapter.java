package com.gtnhblueprints.io;

import java.lang.reflect.Type;

import net.minecraft.item.ItemStack;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gtnewhorizon.gtnhlib.util.data.ItemMeta;
import com.recursive_pineapple.matter_manipulator.common.building.PortableItemStack;
import com.recursive_pineapple.matter_manipulator.common.persist.StaticEnumJsonAdapter;
import com.recursive_pineapple.matter_manipulator.common.persist.UIDJsonAdapter;
import com.recursive_pineapple.matter_manipulator.common.building.providers.AECellItemProvider;
import com.recursive_pineapple.matter_manipulator.common.building.providers.BatteryItemProvider;
import com.recursive_pineapple.matter_manipulator.common.building.providers.ComputerComponentItemProvider;
import com.recursive_pineapple.matter_manipulator.common.building.providers.IItemProvider;
import com.recursive_pineapple.matter_manipulator.common.building.providers.PatternItemProvider;

import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/** Keeps polymorphic inventory providers portable and reads blueprints written before provider tags existed. */
public final class ItemProviderJsonAdapter implements JsonSerializer<IItemProvider>, JsonDeserializer<IItemProvider> {

    private static final String TYPE_FIELD = "$provider";

    /*
     * This Gson deliberately has no IItemProvider adapter. The public blueprint Gson registers this adapter for the
     * entire provider hierarchy so Gson's runtime-type wrapper cannot reflect into a concrete provider. Delegating
     * ordinary provider fields back through that same Gson would recurse, so those fields are handled here instead.
     */
    private static final Gson PROVIDER_FIELDS = new GsonBuilder()
        .registerTypeAdapter(UniqueIdentifier.class, new UIDJsonAdapter())
        .registerTypeAdapter(NBTTagCompound.class, new CompatibleNBTJsonAdapter())
        .registerTypeAdapter(ForgeDirection.class, new StaticEnumJsonAdapter<>(ForgeDirection.class))
        .disableHtmlEscaping()
        .create();

    @Override
    public JsonElement serialize(IItemProvider source, Type type, JsonSerializationContext context) {
        JsonObject object;
        if (source instanceof BatteryItemProvider || source instanceof ComputerComponentItemProvider) {
            ItemStack stack = source.getStack(null, false);
            if (stack == null) throw new JsonParseException("物品提供器没有可保存的物品");
            object = new JsonObject();
            object.add("stack", context.serialize(PortableItemStack.withNBT(stack), PortableItemStack.class));
        } else {
            object = PROVIDER_FIELDS.toJsonTree(source, source.getClass()).getAsJsonObject();
        }
        object.addProperty(TYPE_FIELD, typeName(source));
        return object;
    }

    @Override
    public IItemProvider deserialize(JsonElement json, Type type, JsonDeserializationContext context)
        throws JsonParseException {
        if (!json.isJsonObject()) throw new JsonParseException("物品提供器必须是 JSON 对象");
        JsonObject object = json.getAsJsonObject();
        String provider = object.has(TYPE_FIELD) ? object.get(TYPE_FIELD).getAsString() : inferLegacyType(object);

        switch (provider) {
            case "portable":
                return PROVIDER_FIELDS.fromJson(object, PortableItemStack.class);
            case "ae_cell":
                return PROVIDER_FIELDS.fromJson(object, AECellItemProvider.class);
            case "battery":
                return readBattery(object, context);
            case "computer_component":
                return readComputerComponent(object, context);
            case "pattern":
                return PROVIDER_FIELDS.fromJson(object, PatternItemProvider.class);
            default:
                throw new JsonParseException("未知物品提供器类型: " + provider);
        }
    }

    private static String typeName(IItemProvider provider) {
        if (provider instanceof PortableItemStack) return "portable";
        if (provider instanceof AECellItemProvider) return "ae_cell";
        if (provider instanceof BatteryItemProvider) return "battery";
        if (provider instanceof ComputerComponentItemProvider) return "computer_component";
        if (provider instanceof PatternItemProvider) return "pattern";
        throw new JsonParseException("不支持的物品提供器类型: " + provider.getClass().getName());
    }

    private static String inferLegacyType(JsonObject object) {
        if (object.has("id")) return "portable";
        if (object.has("mCell")) return "ae_cell";
        if (object.has("battery")) return "battery";
        if (object.has("component")) return "computer_component";
        if (object.has("pattern") || object.has("amount")) return "pattern";
        throw new JsonParseException("旧蓝图中的物品提供器类型无法识别");
    }

    private static BatteryItemProvider readBattery(JsonObject object, JsonDeserializationContext context) {
        PortableItemStack portable = readTaggedStack(object, context);
        ItemStack stack = portable.toStack();
        if (stack == null) throw new JsonParseException("电池物品不存在于当前整合包");
        BatteryItemProvider provider = new BatteryItemProvider();
        provider.battery = new ItemMeta(stack.getItem(), stack.getItemDamage());
        return provider;
    }

    private static ComputerComponentItemProvider readComputerComponent(
        JsonObject object,
        JsonDeserializationContext context
    ) {
        PortableItemStack portable = readTaggedStack(object, context);
        ItemStack stack = portable.toStack();
        if (stack == null) throw new JsonParseException("OpenComputers 组件不存在于当前整合包");
        return new ComputerComponentItemProvider(stack);
    }

    private static PortableItemStack readTaggedStack(JsonObject object, JsonDeserializationContext context) {
        if (!object.has("stack")) throw new JsonParseException("物品提供器缺少可移植物品数据");
        return context.deserialize(object.get("stack"), PortableItemStack.class);
    }
}

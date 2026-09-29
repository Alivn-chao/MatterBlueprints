package com.gtnhblueprints.io;

import java.util.BitSet;

import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.recursive_pineapple.matter_manipulator.common.data.WeightedSpecList;
import com.recursive_pineapple.matter_manipulator.common.building.providers.IItemProvider;
import com.recursive_pineapple.matter_manipulator.common.persist.BitSetJsonAdapter;
import com.recursive_pineapple.matter_manipulator.common.persist.StaticEnumJsonAdapter;
import com.recursive_pineapple.matter_manipulator.common.persist.UIDJsonAdapter;
import com.recursive_pineapple.matter_manipulator.common.persist.WeightedListJsonAdapter;

import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public final class BlueprintJson {

    public static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(UniqueIdentifier.class, new UIDJsonAdapter())
        .registerTypeAdapter(NBTTagCompound.class, new CompatibleNBTJsonAdapter())
        .registerTypeAdapter(FluidStack.class, new FluidStackJsonAdapter())
        .registerTypeAdapter(ForgeDirection.class, new StaticEnumJsonAdapter<>(ForgeDirection.class))
        .registerTypeAdapter(WeightedSpecList.class, new WeightedListJsonAdapter())
        .registerTypeAdapter(BitSet.class, new BitSetJsonAdapter())
        // A hierarchy adapter is required here. Gson otherwise replaces the interface adapter with a reflective
        // adapter for the runtime provider class, which can walk into OpenComputers continuations on Java 25.
        .registerTypeHierarchyAdapter(IItemProvider.class, new ItemProviderJsonAdapter())
        .disableHtmlEscaping()
        .create();

    private BlueprintJson() {}
}

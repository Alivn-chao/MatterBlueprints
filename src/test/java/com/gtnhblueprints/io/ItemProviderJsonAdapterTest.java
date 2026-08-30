package com.gtnhblueprints.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.recursive_pineapple.matter_manipulator.common.building.PortableItemStack;
import com.recursive_pineapple.matter_manipulator.common.building.InventoryAnalysis;
import com.recursive_pineapple.matter_manipulator.common.building.providers.AECellItemProvider;
import com.recursive_pineapple.matter_manipulator.common.building.providers.IItemProvider;

class ItemProviderJsonAdapterTest {

    @Test
    void readsLegacyPortableStackWithoutTypeTag() {
        IItemProvider provider = BlueprintJson.GSON
            .fromJson("{\"id\":\"gregtech:gt.metaitem.01\",\"m\":32104}", IItemProvider.class);

        assertTrue(provider instanceof PortableItemStack);
        assertEquals(32104, ((PortableItemStack) provider).getMeta());
    }

    @Test
    void readsLegacyAeCellWithoutTypeTag() {
        IItemProvider provider = BlueprintJson.GSON.fromJson(
            "{\"mCell\":{\"id\":\"appliedenergistics2:item.ItemAdvancedStorageCell.256k\"},"
                + "\"mUpgrades\":[],\"mConfig\":[],\"mFuzzyMode\":0}",
            IItemProvider.class);

        assertTrue(provider instanceof AECellItemProvider);
    }

    @Test
    void writesStableTypeTagAndRoundTrips() {
        IItemProvider provider = new AECellItemProvider();
        String encoded = BlueprintJson.GSON.toJson(provider, IItemProvider.class);
        IItemProvider decoded = BlueprintJson.GSON.fromJson(encoded, IItemProvider.class);

        assertTrue(encoded.contains("\"$provider\":\"ae_cell\""));
        assertTrue(decoded instanceof AECellItemProvider);
    }

    @Test
    void writesProvidersInsideRuntimeTypedInventoryArraysWithoutReflectingIntoTheirClasses() {
        InventoryAnalysis inventory = new InventoryAnalysis();
        inventory.mItems = new IItemProvider[] { new AECellItemProvider() };

        String encoded = BlueprintJson.GSON.toJson(inventory);
        InventoryAnalysis decoded = BlueprintJson.GSON.fromJson(encoded, InventoryAnalysis.class);

        assertTrue(encoded.contains("\"$provider\":\"ae_cell\""));
        assertTrue(decoded.mItems[0] instanceof AECellItemProvider);
    }

}

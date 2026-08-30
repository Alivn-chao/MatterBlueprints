package com.gtnhblueprints.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.recursive_pineapple.matter_manipulator.common.building.AEAnalysisResult;
import com.recursive_pineapple.matter_manipulator.common.building.GTAnalysisResult;

class TraitRoundTripTest {

    @Test
    void preservesGregTechTraits() {
        GTAnalysisResult source = new GTAnalysisResult();
        source.mConnections = 0x15;
        source.mGTColour = 7;
        source.mGTFront = ForgeDirection.NORTH;
        source.mGTMainFacing = ForgeDirection.UP;
        source.mGTFlags = 0x2345;
        source.mStrongRedstone = 0x0d;
        source.mGTCustomName = "测试机器";
        source.mGTGhostCircuit = 24;
        source.mGTFluidLock = "molten.solderingalloy";
        source.mGTMode = 9;
        source.mGTData = new JsonParser().parse("{\"mode\":3}");
        source.mTTParams = new double[] { 1.25, 2.5, 5.0 };
        source.mAmperes = 64;
        source.mFluidPipeRestriction = 0x06;
        source.mMaxParallels = 128;

        GTAnalysisResult decoded = BlueprintJson.GSON
            .fromJson(BlueprintJson.GSON.toJson(source), GTAnalysisResult.class);

        assertEquals(source.mConnections, decoded.mConnections);
        assertEquals(source.mGTColour, decoded.mGTColour);
        assertEquals(source.mGTFront, decoded.mGTFront);
        assertEquals(source.mGTMainFacing, decoded.mGTMainFacing);
        assertEquals(source.mGTFlags, decoded.mGTFlags);
        assertEquals(source.mStrongRedstone, decoded.mStrongRedstone);
        assertEquals(source.mGTCustomName, decoded.mGTCustomName);
        assertEquals(source.mGTGhostCircuit, decoded.mGTGhostCircuit);
        assertEquals(source.mGTFluidLock, decoded.mGTFluidLock);
        assertEquals(source.mGTMode, decoded.mGTMode);
        assertEquals(source.mGTData, decoded.mGTData);
        assertArrayEquals(source.mTTParams, decoded.mTTParams);
        assertEquals(source.mAmperes, decoded.mAmperes);
        assertEquals(source.mFluidPipeRestriction, decoded.mFluidPipeRestriction);
        assertEquals(source.mMaxParallels, decoded.mMaxParallels);
    }

    @Test
    void preservesAppliedEnergisticsTraits() {
        String json = "{\"mAEColour\":\"Blue\",\"mAEUp\":\"UP\",\"mAEForward\":\"WEST\","
            + "\"mAEConfig\":{\"priority\":42},\"mAECustomName\":\"主接口\","
            + "\"mAEParts\":[{\"mP2POutput\":true,\"mP2PFreq\":123456789}],"
            + "\"mAECells\":{\"mFuzzy\":false,\"mItems\":[null]},"
            + "\"mAEPatterns\":{\"mFuzzy\":false,\"mItems\":[null]}}";

        AEAnalysisResult source = BlueprintJson.GSON.fromJson(json, AEAnalysisResult.class);
        AEAnalysisResult decoded = BlueprintJson.GSON
            .fromJson(BlueprintJson.GSON.toJson(source), AEAnalysisResult.class);

        assertEquals(source.mAEColour, decoded.mAEColour);
        assertEquals(source.mAEUp, decoded.mAEUp);
        assertEquals(source.mAEForward, decoded.mAEForward);
        assertEquals(source.mAEConfig, decoded.mAEConfig);
        assertEquals(source.mAECustomName, decoded.mAECustomName);
        assertNotNull(decoded.mAEParts);
        assertEquals(source.mAEParts[0].mP2POutput, decoded.mAEParts[0].mP2POutput);
        assertEquals(source.mAEParts[0].mP2PFreq, decoded.mAEParts[0].mP2PFreq);
        assertNotNull(decoded.mAECells);
        assertNotNull(decoded.mAEPatterns);
    }
}

package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HostedGeneratorCompatibilityTest {

    @Test
    void recognizesSupportedGeneratorFamilies() {
        assertTrue(
            HostedGeneratorSupport.isSupportedClassName(
                "gregtech.common.tileentities.machines.multi.turbines.MTELargeTurbineGas"));
        assertTrue(
            HostedGeneratorSupport.isSupportedClassName(
                "gregtech.common.tileentities.machines.multi.xlturbines.MTEXLTurbinePlasma"));
        assertTrue(
            HostedGeneratorSupport.isSupportedClassName(
                "gregtech.common.tileentities.machines.multi.MTELargeNaquadahReactor"));
        assertTrue(
            HostedGeneratorSupport.isSupportedClassName(
                "goodgenerator.blocks.tileEntity.MTEMultiNqGeneratorLegacy"));
        assertTrue(
            HostedGeneratorSupport.isSupportedClassName(
                "gregtech.common.tileentities.machines.multi.MTELargeCombustionEngine"));
        assertTrue(
            HostedGeneratorSupport.isSupportedClassName(
                "gregtech.common.tileentities.machines.multi.MTEUniversalChemicalFuelEngine"));
    }

    @Test
    void doesNotTreatOrdinaryConsumersAsGenerators() {
        assertFalse(
            HostedGeneratorSupport.isSupportedClassName(
                "gregtech.common.tileentities.machines.multi.MTEIndustrialCuttingMachine"));
        assertFalse(HostedGeneratorSupport.isSupportedClassName(null));
    }
}

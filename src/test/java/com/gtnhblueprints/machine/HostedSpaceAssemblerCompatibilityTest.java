package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HostedSpaceAssemblerCompatibilityTest {

    @Test
    void recognizesAllSpaceAssemblerModuleTiers() {
        String base = "gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleAssembler";
        assertTrue(HostedSpaceAssemblerSupport.isSupportedClassName(base + "$TileEntityModuleAssemblerT1"));
        assertTrue(HostedSpaceAssemblerSupport.isSupportedClassName(base + "$TileEntityModuleAssemblerT2"));
        assertTrue(HostedSpaceAssemblerSupport.isSupportedClassName(base + "$TileEntityModuleAssemblerT3"));
    }

    @Test
    void doesNotMatchOtherSpaceElevatorModules() {
        assertFalse(
            HostedSpaceAssemblerSupport.isSupportedClassName(
                "gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner$TileEntityModuleMinerT1"));
        assertFalse(HostedSpaceAssemblerSupport.isSupportedClassName(null));
    }
}

package com.gtnhblueprints.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlueprintGeometryTest {

    @Test
    void oldBlueprintUsesNegativeDirectionOnlyOnce() {
        assertEquals(-10, BlueprintGeometry.sourceDelta(11, null, -10));
        assertEquals(-5, BlueprintGeometry.sourceDelta(6, null, -5));
    }

    @Test
    void newBlueprintUsesStoredSelectionDirection() {
        assertEquals(-10, BlueprintGeometry.sourceDelta(11, -10, 0));
        assertEquals(10, BlueprintGeometry.sourceDelta(11, 10, -2));
    }

    @Test
    void rejectsStoredDeltaThatDoesNotMatchSize() {
        assertEquals(-10, BlueprintGeometry.sourceDelta(11, 7, -1));
    }
}

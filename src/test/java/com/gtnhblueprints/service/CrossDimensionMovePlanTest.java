package com.gtnhblueprints.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;

class CrossDimensionMovePlanTest {

    @Test
    void sameCoordinatesInDifferentDimensionsAreNotAnOverlap() {
        CrossDimensionMovePlan plan = plan(new Location(0, 8, 64, 8), new Location(0, 9, 65, 9), new Location(-1, 8, 64, 8));
        assertEquals(8, plan.count);
        assertEquals(new Location(0, 8, 64, 8), plan.source(0));
        assertEquals(new Location(-1, 8, 64, 8), plan.target(plan.source(0)));
        assertEquals(0, plan.offsetX);
    }

    @Test
    void reversedSelectionKeepsCornerAAnchoredToDestination() {
        Location a = new Location(7, 20, 70, 30), b = new Location(7, 18, 68, 28), c = new Location(0, -10, 40, 100);
        CrossDimensionMovePlan plan = plan(a, b, c);
        assertEquals(c, plan.target(a));
        assertEquals(new Location(0, -12, 38, 98), plan.target(b));
        assertEquals(new Location(7, 18, 68, 28), plan.source(0));
        assertEquals(new Location(7, 20, 70, 30), plan.source(26));
    }

    @Test
    void lazilyEnumeratesEveryBlockOnceAcrossNegativeChunkCoordinates() {
        CrossDimensionMovePlan plan = plan(new Location(0, -17, 10, -1), new Location(0, 16, 11, 1), new Location(42, 100, 100, 200));
        Set<Location> source = new HashSet<>(), target = new HashSet<>();
        for (int i = 0; i < plan.count; i++) {
            Location location = plan.source(i);
            assertTrue(source.add(location));
            assertTrue(target.add(plan.target(location)));
            assertEquals(0, location.worldId);
            assertEquals(42, plan.target(location).worldId);
        }
        assertEquals(34 * 2 * 3, source.size());
        assertEquals(source.size(), target.size());
    }

    @Test
    void singleBlockIsIncludedAndIndexIsBounded() {
        CrossDimensionMovePlan plan = plan(new Location(0, 0, 0, 0), new Location(0, 0, 0, 0), new Location(1, 0, 255, 0));
        assertEquals(1, plan.count);
        assertEquals(new Location(1, 0, 255, 0), plan.target(plan.source(0)));
        assertThrows(IndexOutOfBoundsException.class, () -> plan.source(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> plan.source(1));
    }

    @Test
    void rejectsMissingMixedSourceAndSameDimensionSelections() {
        Location a = new Location(0, 0, 64, 0), b = new Location(1, 1, 65, 1), c = new Location(2, 0, 64, 0);
        assertThrows(IllegalArgumentException.class, () -> plan(null, a, c));
        assertThrows(IllegalArgumentException.class, () -> plan(a, null, c));
        assertThrows(IllegalArgumentException.class, () -> plan(a, a, null));
        assertThrows(IllegalArgumentException.class, () -> plan(a, b, c));
        assertThrows(IllegalArgumentException.class, () -> plan(a, a, a));
    }

    @Test
    void enforcesSpanAndVolumeBeforeAllocatingOrIterating() {
        Location a = new Location(0, 0, 64, 0), b = new Location(0, 15, 79, 15), c = new Location(1, 0, 64, 0);
        assertThrows(IllegalArgumentException.class, () -> new CrossDimensionMovePlan(a, b, c, 15, 10000));
        assertThrows(IllegalArgumentException.class, () -> new CrossDimensionMovePlan(a, b, c, 16, 4095));
        assertEquals(4096, new CrossDimensionMovePlan(a, b, c, 16, 4096).count);
    }

    @Test
    void checksTranslatedBoundsAndSourceBounds() {
        Location a = new Location(0, 0, 10, 0), b = new Location(0, 1, 11, 1);
        assertThrows(IllegalArgumentException.class, () -> plan(a, b, new Location(1, 0, 255, 0)));
        assertThrows(IllegalArgumentException.class, () -> plan(a, b, new Location(1, 0, -1, 0)));
        assertThrows(IllegalArgumentException.class, () -> plan(a, b, new Location(1, 29_999_999, 10, 0)));
        assertThrows(IllegalArgumentException.class, () -> plan(new Location(0, 0, -1, 0), a, new Location(1, 0, 10, 0)));
    }

    @Test
    void hugeIntegerSelectionsCannotWrapAroundToAValidVolume() {
        assertThrows(IllegalArgumentException.class, () -> plan(new Location(0, Integer.MIN_VALUE, 64, 0),
            new Location(0, Integer.MAX_VALUE, 64, 0), new Location(1, 0, 64, 0)));
    }

    @Test
    void inputLocationsAreNotRetainedOrMutated() {
        Location a = new Location(0, 1, 64, 1), b = new Location(0, 2, 65, 2), c = new Location(1, 10, 70, 10);
        CrossDimensionMovePlan plan = plan(a, b, c);
        a.x = 1000;
        b.worldId = 7;
        c.x = -500;
        assertEquals(new Location(0, 1, 64, 1), plan.source(0));
        assertEquals(new Location(1, 10, 70, 10), plan.target(plan.source(0)));
    }

    private static CrossDimensionMovePlan plan(Location a, Location b, Location c) {
        return new CrossDimensionMovePlan(a, b, c, 512, 250_000);
    }
}

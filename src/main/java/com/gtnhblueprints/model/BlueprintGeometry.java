package com.gtnhblueprints.model;

public final class BlueprintGeometry {

    private BlueprintGeometry() {}

    public static int sourceDelta(int size, Integer storedDelta, int minimumBlockCoordinate) {
        int extent = Math.max(0, size - 1);
        if (storedDelta != null && Math.abs((long) storedDelta) == extent) return storedDelta;
        return minimumBlockCoordinate < 0 ? -extent : extent;
    }
}

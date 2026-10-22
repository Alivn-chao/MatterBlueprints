package com.gtnhblueprints.service;

import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;

/** Immutable geometry; a destination is the image of corner A, even when A is not the minimum. */
public final class CrossDimensionMovePlan {

    public final int sourceDimension, targetDimension;
    public final int minX, minY, minZ, maxX, maxY, maxZ;
    public final int offsetX, offsetY, offsetZ;
    public final int count;
    private final int sizeX, sizeZ;

    public CrossDimensionMovePlan(Location a, Location b, Location destination, int maxSpan, int maxBlocks) {
        if (a == null || b == null || destination == null) throw new IllegalArgumentException("请先标记剪切区域 A、B 和目标 C");
        if (a.worldId != b.worldId) throw new IllegalArgumentException("剪切区域 A、B 必须位于同一维度");
        if (a.worldId == destination.worldId) throw new IllegalArgumentException("此队列仅处理跨维度移动");
        sourceDimension = a.worldId;
        targetDimension = destination.worldId;
        minX = Math.min(a.x, b.x);
        minY = Math.min(a.y, b.y);
        minZ = Math.min(a.z, b.z);
        maxX = Math.max(a.x, b.x);
        maxY = Math.max(a.y, b.y);
        maxZ = Math.max(a.z, b.z);
        long sx = (long) maxX - minX + 1, sy = (long) maxY - minY + 1, sz = (long) maxZ - minZ + 1;
        if (sx > maxSpan || sy > maxSpan || sz > maxSpan) throw new IllegalArgumentException("移动区域超过轴长限制 " + maxSpan);
        long volume = sx * sy * sz;
        if (volume > maxBlocks) throw new IllegalArgumentException("移动区域超过方块数限制 " + maxBlocks);
        sizeX = (int) sx;
        sizeZ = (int) sz;
        count = (int) volume;
        offsetX = Math.toIntExact((long) destination.x - a.x);
        offsetY = Math.toIntExact((long) destination.y - a.y);
        offsetZ = Math.toIntExact((long) destination.z - a.z);
        validateBounds(minX, minY, minZ, maxX, maxY, maxZ);
        validateBounds((long) minX + offsetX, (long) minY + offsetY, (long) minZ + offsetZ,
            (long) maxX + offsetX, (long) maxY + offsetY, (long) maxZ + offsetZ);
    }

    private static void validateBounds(long x1, long y1, long z1, long x2, long y2, long z2) {
        if (y1 < 0 || y2 > 255 || x1 < -30_000_000 || x2 >= 30_000_000
            || z1 < -30_000_000 || z2 >= 30_000_000) {
            throw new IllegalArgumentException("移动区域超出世界坐标或高度限制");
        }
    }

    public Location source(int index) {
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException();
        return new Location(sourceDimension, minX + index / sizeZ % sizeX,
            minY + index / (sizeX * sizeZ), minZ + index % sizeZ);
    }

    public Location target(Location source) {
        return new Location(targetDimension, source.x + offsetX, source.y + offsetY, source.z + offsetZ);
    }
}

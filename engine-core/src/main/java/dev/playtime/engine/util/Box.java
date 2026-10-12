package dev.playtime.engine.util;

/** Axis-aligned block volume, inclusive on both corners. Used for trigger volumes and areas. */
public record Box(String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Box {
        if (dimension == null || dimension.isBlank()) dimension = BlockRef.OVERWORLD;
        int ax = Math.min(minX, maxX), bx = Math.max(minX, maxX);
        int ay = Math.min(minY, maxY), by = Math.max(minY, maxY);
        int az = Math.min(minZ, maxZ), bz = Math.max(minZ, maxZ);
        minX = ax; maxX = bx; minY = ay; maxY = by; minZ = az; maxZ = bz;
    }

    public boolean contains(String dim, double x, double y, double z) {
        return dimension.equals(dim)
                && x >= minX && x < maxX + 1
                && y >= minY && y < maxY + 1
                && z >= minZ && z < maxZ + 1;
    }

    public boolean contains(BlockRef pos) {
        return contains(pos.dimension(), pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}

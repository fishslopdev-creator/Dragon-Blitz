package dev.playtime.engine.util;

/** A block position in a specific dimension. Used to anchor devices, doors and markers. */
public record BlockRef(String dimension, int x, int y, int z) {
    public static final String OVERWORLD = "minecraft:overworld";

    public BlockRef {
        if (dimension == null || dimension.isBlank()) {
            dimension = OVERWORLD;
        }
    }

    public static BlockRef of(int x, int y, int z) {
        return new BlockRef(OVERWORLD, x, y, z);
    }

    public Vec3 center() {
        return new Vec3(x + 0.5, y + 0.5, z + 0.5);
    }

    public long chunkKey() {
        long cx = x >> 4;
        long cz = z >> 4;
        return (cx & 0xFFFFFFFFL) | (cz << 32);
    }

    public double distanceSq(BlockRef o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public String toString() {
        return dimension + "@" + x + "," + y + "," + z;
    }
}

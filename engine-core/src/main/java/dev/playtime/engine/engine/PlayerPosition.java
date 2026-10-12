package dev.playtime.engine.engine;

import dev.playtime.engine.util.Vec3;

public record PlayerPosition(String dimension, double x, double y, double z) {
    public Vec3 vec() {
        return new Vec3(x, y, z);
    }
}

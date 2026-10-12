package dev.playtime.engine.grabpack;

import dev.playtime.engine.util.Vec3;

/**
 * Bounded velocity for pulling the player towards an attach point. The result is capped
 * so the mechanic cannot be used to fling players through walls or across the map.
 */
public final class SelfPull {
    public static final double STOP_DISTANCE = 1.5;

    private SelfPull() {}

    public static Vec3 velocity(Vec3 player, Vec3 anchor, double maxSpeed) {
        Vec3 delta = anchor.subtract(player);
        double dist = delta.length();
        if (dist <= STOP_DISTANCE) return Vec3.ZERO;
        double speed = Math.min(maxSpeed, Math.max(0.15, (dist - STOP_DISTANCE) * 0.25));
        return delta.normalize().scale(speed);
    }
}

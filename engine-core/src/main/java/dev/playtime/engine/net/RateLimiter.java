package dev.playtime.engine.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Token bucket per player and request kind. Stops a modified client from spamming
 * fire/select packets; legitimate play never reaches the limit.
 */
public final class RateLimiter {
    private final int capacity;
    private final double refillPerTick;
    private final Map<String, double[]> buckets = new HashMap<>();

    public RateLimiter(int capacity, double refillPerTick) {
        this.capacity = capacity;
        this.refillPerTick = refillPerTick;
    }

    public boolean tryAcquire(UUID player, String kind, long tick) {
        String key = player + "|" + kind;
        double[] b = buckets.computeIfAbsent(key, k -> new double[]{capacity, tick});
        b[0] = Math.min(capacity, b[0] + (tick - b[1]) * refillPerTick);
        b[1] = tick;
        if (b[0] < 1) return false;
        b[0] -= 1;
        return true;
    }

    public void forget(UUID player) {
        buckets.keySet().removeIf(k -> k.startsWith(player + "|"));
    }
}

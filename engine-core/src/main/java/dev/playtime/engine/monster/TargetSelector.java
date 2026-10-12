package dev.playtime.engine.monster;

import dev.playtime.engine.config.TargetPolicy;
import dev.playtime.engine.util.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Picks one player among several valid targets according to the world's policy. */
public final class TargetSelector {
    private TargetSelector() {}

    public static PlayerView pick(List<PlayerView> candidates, TargetPolicy policy, Vec3 from, UUID preferred, long seed) {
        if (candidates.isEmpty()) return null;
        if (preferred != null) {
            for (PlayerView p : candidates) if (p.id().equals(preferred)) return p;
        }
        Comparator<PlayerView> nearest = Comparator.comparingDouble(p -> p.position().distanceSq(from));
        return switch (policy) {
            case NEAREST, TRIGGERING_PLAYER -> candidates.stream().min(nearest).orElse(null);
            case LOWEST_HEALTH -> candidates.stream()
                    .min(Comparator.comparingDouble((PlayerView p) -> p.health()).thenComparing(nearest)).orElse(null);
            case RANDOM -> candidates.stream()
                    .sorted(Comparator.comparing(p -> p.id().toString()))
                    .toList().get((int) Math.floorMod(seed, (long) candidates.size()));
            case HOST -> candidates.stream().filter(PlayerView::isHost).findFirst()
                    .orElse(candidates.stream().min(nearest).orElse(null));
        };
    }
}

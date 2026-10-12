package dev.playtime.engine.monster;

import dev.playtime.engine.util.Vec3;

import java.util.List;

/** World queries for one monster. Implemented by the monster entity in the game layer. */
public interface MonsterSenses {
    Vec3 position();

    /** Normalised horizontal facing direction. */
    Vec3 facing();

    /** Candidate players in the same dimension within a sensible radius (spectators excluded). */
    List<PlayerView> players();

    boolean hasLineOfSight(Vec3 from, Vec3 to);
}

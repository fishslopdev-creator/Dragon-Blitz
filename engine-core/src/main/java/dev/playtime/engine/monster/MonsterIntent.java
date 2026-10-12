package dev.playtime.engine.monster;

import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;

import java.util.UUID;

/**
 * What the monster wants to do until its next decision. The entity turns this into
 * navigation, animation and sound; {@code teleportTo} is only set by stuck recovery.
 */
public record MonsterIntent(MonsterState state, Vec3 moveTarget, double speed, Vec3 lookAt, String animation,
                            UUID attackTarget, Vec3 teleportTo, Id sound, boolean despawn) {
    public static MonsterIntent idle(MonsterState state, String animation) {
        return new MonsterIntent(state, null, 0, null, animation, null, null, null, false);
    }
}

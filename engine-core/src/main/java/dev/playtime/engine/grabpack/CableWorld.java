package dev.playtime.engine.grabpack;

import dev.playtime.engine.util.Vec3;

/** What the cable simulation needs to know about the world. Implemented by the game layer. */
public interface CableWorld {
    boolean isLoaded(Vec3 position);

    /** True when nothing solid lies between the two points (ignoring the target block itself). */
    boolean isLineClear(Vec3 from, Vec3 to);

    /**
     * Re-resolves an attached target each tick. Moving entities report their new
     * position; removed entities or broken devices return {@code null}.
     */
    TargetInfo refresh(TargetInfo target);
}

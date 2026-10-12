package dev.playtime.engine.monster;

/** What a monster does when it cannot make progress towards its goal. */
public enum RecoveryStrategy {
    /** Teleport to the goal (only when no player can see either spot). */
    TELEPORT,
    /** Skip to the next waypoint in its route. */
    SKIP_WAYPOINT,
    /** Walk back to the nearest route waypoint. */
    RETURN_TO_ROUTE,
    /** Stand still and wait for the obstacle to clear. */
    WAIT,
    /** Remove the monster; the encounter decides what happens next. */
    DESPAWN
}

package dev.playtime.engine.puzzle;

public enum ResetPolicy {
    /** Restart immediately after a failure. */
    ON_FAILURE,
    /** Reset when a participating player dies (if the world resets active puzzles on death). */
    ON_DEATH,
    /** Only an action, connection or admin command resets it. */
    MANUAL,
    /** Never resets, even after completion. */
    NEVER
}

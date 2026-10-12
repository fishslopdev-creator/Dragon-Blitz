package dev.playtime.engine.puzzle;

public enum PuzzleStatus {
    /** Waiting for its activation conditions. */
    INACTIVE,
    ACTIVE,
    COMPLETED,
    /** Failed and waiting for a reset (only when the reset policy does not reset automatically). */
    FAILED
}

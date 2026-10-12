package dev.playtime.engine.chase;

public enum FailureOutcome {
    /** Send caught players back to the chase checkpoint (or their save's checkpoint). */
    RESPAWN_AT_CHECKPOINT,
    /** Re-arm the chase so it can be triggered again (temporary state is reset). */
    RESTART_CHASE,
    /** Reset the puzzles listed in {@link ChaseDefinition#resetPuzzles}. */
    RESET_PUZZLES,
    /** Re-open the main menu (if the world's menu is enabled). */
    RETURN_TO_MENU
}

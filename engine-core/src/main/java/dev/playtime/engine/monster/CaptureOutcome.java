package dev.playtime.engine.monster;

public enum CaptureOutcome {
    /** Deal {@link MonsterDefinition#attackDamage} damage. */
    DAMAGE,
    /** Kill the player outright (jumpscare). */
    KILL,
    /** "Caught": no damage, the active chase or encounter handles the failure (checkpoint/restart). */
    CATCH
}

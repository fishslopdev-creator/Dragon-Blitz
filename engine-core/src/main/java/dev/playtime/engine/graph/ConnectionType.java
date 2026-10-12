package dev.playtime.engine.graph;

/** What a link does to its destination when the source signals. */
public enum ConnectionType {
    /** Power flows from source to destination (handled by the power network, not by signals). */
    POWER,
    /** Destination follows the source: ON activates, OFF deactivates. */
    ACTIVATE,
    /** ON deactivates the destination. */
    DEACTIVATE,
    /** Each ON or PULSE flips the destination. */
    TOGGLE,
    OPEN_DOOR,
    CLOSE_DOOR,
    UNLOCK_DOOR,
    LOCK_DOOR,
    ENABLE,
    DISABLE,
    START_CHASE,
    STOP_CHASE,
    TRIGGER_EVENT,
    RESET_PUZZLE,
    /** Reports the signal to the destination's puzzle as an input. */
    PUZZLE_INPUT;

    /** Types whose delivery can re-emit signals immediately and therefore form loops. */
    public boolean propagates() {
        return this == ACTIVATE || this == DEACTIVATE || this == TOGGLE || this == ENABLE || this == DISABLE;
    }
}

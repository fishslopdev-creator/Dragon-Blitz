package dev.playtime.engine.puzzle;

public enum PuzzleEventType {
    DEVICE_CHANGED,
    DEVICE_PULSED,
    POWER_CHANGED,
    SOCKET_CHANGED,
    CARD_SWIPED,
    HAND_SCANNED,
    PLAYER_REACHED,
    PUZZLE_COMPLETED,
    /** Generic input from a PUZZLE_INPUT connection. */
    INPUT
}

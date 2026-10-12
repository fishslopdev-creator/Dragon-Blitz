package dev.playtime.engine.config;

/** Who may restart or reset shared encounters and puzzles. */
public enum ResetAuthority {
    OPERATORS_ONLY,
    HOST_AND_OPERATORS,
    ANY_PARTICIPANT
}

package dev.playtime.engine.config;

/** How a monster picks between several valid players. */
public enum TargetPolicy {
    NEAREST,
    TRIGGERING_PLAYER,
    LOWEST_HEALTH,
    RANDOM,
    HOST
}

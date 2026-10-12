package dev.playtime.engine.profile;

public enum AssignmentType {
    /** A specific player by UUID or name. */
    PLAYER,
    /** Any member of a scoreboard team. */
    TEAM,
    /** Anyone who first spawns inside an area. */
    AREA,
    /** Active when a story flag ("scenario") is set on the world. */
    SCENARIO
}

package dev.playtime.engine.config;

/** Situations in which a world's custom main menu may be shown. */
public enum MenuTrigger {
    /** Every time the player joins the world in Survival. */
    EVERY_LOGIN,
    /** Only the first time this player ever enters the world. */
    FIRST_ENTRY,
    /** When the player respawns after dying. */
    AFTER_DEATH,
    /** When the player's game mode changes to Survival (for example via /gamemode survival). */
    SURVIVAL_SWITCH
}

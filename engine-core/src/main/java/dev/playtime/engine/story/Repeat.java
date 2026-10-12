package dev.playtime.engine.story;

public enum Repeat {
    /** Once per save (per player in individual mode, once for everyone in shared mode). */
    ONCE_PER_SAVE,
    /** Once for the whole world, whoever triggers it. */
    ONCE_PER_WORLD,
    /** Every time it triggers (subject to cooldown). */
    ALWAYS
}

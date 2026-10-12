package dev.playtime.engine.config;

/** How story progress is owned in multiplayer. */
public enum ProgressionMode {
    /** Each player has their own save; puzzles they solve only count for them. */
    INDIVIDUAL,
    /** Everyone on the world shares one save (co-operative). */
    SHARED,
    /** One host player owns the save; guests play along but cannot change it on their own. */
    HOST_OWNED
}

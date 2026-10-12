package dev.playtime.engine.access;

public enum DoorOperation {
    /** The player uses the door (or its reader/scanner) and the access rule decides. */
    PLAYER,
    /** Only connections, actions or chases open it. */
    SIGNAL,
    /** Open exactly while powered (and while the access rule's conditions hold). */
    POWER
}

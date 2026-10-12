package dev.playtime.engine.access;

/** Saved world state of one door. */
public class DoorState {
    public boolean open;
    /** Locked by a story action or chase; overrides every other way of opening. */
    public boolean forcedLocked;
    /** Game tick at which a timed door closes again (0 = none). */
    public long closeAtTick;
}

package dev.playtime.engine.monster;

/**
 * High-level AI personality. Each profile changes how the shared state machine moves
 * between states, so monsters do not all share one pursue-and-attack loop.
 */
public enum BehaviourProfile {
    /** Patrols, chases on sight, searches the last known position (tall pursuers). */
    STALKER,
    /** Follows routes closely and moves in fast bursts; can be obstructed by doors. */
    CRAWLER,
    /** Advances only while unobserved and backs off when watched. */
    STEALTH,
    /** Waits hidden until a player comes close, then springs out for a short pursuit. */
    AMBUSHER,
    /** Long memory, long searches, investigates every noise. */
    HUNTER,
    /** Nearly blind; tracks by sound. */
    SOUND_HUNTER,
    /** Small, fast, numerous; chases on any detection and scatters when stunned. */
    SWARM,
    /** Driven by scripted phases and chase stages; only fights when told to. */
    BOSS,
    /** Never attacks; idles, follows routes and faces players for dialogue. */
    NPC,
    /** Does nothing on its own; only follows commands from chases and story actions. */
    SCRIPTED
}

package dev.playtime.engine.hand;

import dev.playtime.engine.util.Id;

/**
 * Built-in ability ids understood by the engine. Maps and addons may define further
 * ability ids; the engine ignores ids it has no handler for and the validator warns.
 */
public final class HandAbilities {
    /** Attach to and pull small objects, levers and handles. */
    public static final Id GRAB = Id.of("grab");
    /** Reel an attached object towards the player. */
    public static final Id PULL = Id.of("pull");
    /** Press buttons and use generic hand-interactable blocks. */
    public static final Id INTERACT = Id.of("interact");
    /** Carry power from a source to a receiver along the cable while attached. */
    public static final Id CONDUCT = Id.of("conduct");
    /** Hold an electrical charge after letting go of a source (Green Hand style). */
    public static final Id STORE_CHARGE = Id.of("store_charge");
    /** Trigger matching jump pads that launch the player (Purple/Rocket Hand). */
    public static final Id LAUNCH_PAD = Id.of("launch_pad");
    /** Fire flares that light an area, burn flammable targets and scare configured monsters. */
    public static final Id FLARE = Id.of("flare");
    /** Unlock scanners and terminals that require this hand. */
    public static final Id SCANNER_ACCESS = Id.of("scanner_access");
    /** Attach, charge, then release a pressurised blast that pushes or breaks targets. */
    public static final Id PRESSURE_BLAST = Id.of("pressure_blast");
    /** Emit a short-range pulse that wakes pulse receivers (Pulse Hand). */
    public static final Id PULSE = Id.of("pulse");
    /** Pull the player towards an attach point when the map allows it. */
    public static final Id SELF_PULL = Id.of("self_pull");
    /** Collect designated items by grabbing them. */
    public static final Id RETRIEVE = Id.of("retrieve");

    private HandAbilities() {}
}

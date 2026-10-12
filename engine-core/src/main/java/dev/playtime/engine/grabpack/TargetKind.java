package dev.playtime.engine.grabpack;

/** Categories of thing a GrabPack hand can be fired at. Hands opt in to the kinds they support. */
public enum TargetKind {
    /** Generic hand-interactable block (Red Hand style "use"). */
    INTERACTIVE,
    LEVER,
    HANDLE,
    BUTTON,
    /** Hand scanner; access is decided by the scanner's rule, not by the target kind. */
    SCANNER,
    POWER_SOURCE,
    POWER_RECEIVER,
    /** Small entity or object that can be reeled in. */
    PULLABLE,
    COLLECTIBLE,
    /** Fixed point the player may pull themselves towards (if the map allows it). */
    ATTACH_POINT,
    LAUNCH_PAD,
    PULSE_RECEIVER,
    /** Surface or object a pressure blast can push or break. */
    BREAKABLE,
    /** Surface a flare can stick to. */
    SURFACE
}

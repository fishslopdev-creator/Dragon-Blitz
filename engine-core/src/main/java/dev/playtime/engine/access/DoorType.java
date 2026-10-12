package dev.playtime.engine.access;

/** Door families. The type picks sensible defaults for {@link DoorOperation} and animation speed. */
public enum DoorType {
    LOCKED(DoorOperation.PLAYER),
    KEY(DoorOperation.PLAYER),
    KEYCARD(DoorOperation.PLAYER),
    SECURITY_CLEARANCE(DoorOperation.PLAYER),
    HAND_SCANNER(DoorOperation.PLAYER),
    MULTI_HAND(DoorOperation.PLAYER),
    POWERED(DoorOperation.POWER),
    CIRCUIT(DoorOperation.SIGNAL),
    BUTTON(DoorOperation.SIGNAL),
    TIMED(DoorOperation.SIGNAL),
    HEAVY_MECHANICAL(DoorOperation.SIGNAL),
    STORY(DoorOperation.PLAYER),
    CHASE(DoorOperation.SIGNAL),
    PUZZLE(DoorOperation.PLAYER);

    public final DoorOperation defaultOperation;

    DoorType(DoorOperation op) {
        this.defaultOperation = op;
    }
}

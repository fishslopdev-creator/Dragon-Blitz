package dev.playtime.engine.access;

import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;

/**
 * A door managed by a Door Controller block. Multiblock doors list their size; the
 * controller at {@link #anchor} owns every segment so the door opens as one piece.
 */
public class DoorDefinition {
    public Id id;
    public String displayName;
    public DoorType type = DoorType.LOCKED;
    /** Overrides the type's default when set. */
    public DoorOperation operation;
    public AccessRule access = new AccessRule();
    public BlockRef anchor;
    public int width = 1;
    public int height = 2;
    /** Door faces: north, south, east or west. */
    public String facing = "north";
    /** Auto-close after this many ticks (0 = stay open). */
    public int openTicks;
    /** Once opened by a player the door no longer checks access for that save. */
    public boolean permanentUnlock = true;
    public boolean startsOpen;
    /** Ticks the open/close animation takes (heavy doors are slower). */
    public int animationTicks = 12;
    public Id model;
    public Id openSound = Id.of("door.open");
    public Id closeSound = Id.of("door.close");
    public Id deniedSound = Id.of("door.denied");

    public DoorOperation effectiveOperation() {
        return operation != null ? operation : type.defaultOperation;
    }

    public DoorDefinition normalize() {
        if (type == null) type = DoorType.LOCKED;
        if (access == null) access = new AccessRule();
        width = Math.max(1, Math.min(16, width));
        height = Math.max(1, Math.min(16, height));
        if (type == DoorType.HEAVY_MECHANICAL && animationTicks == 12) animationTicks = 60;
        if (type == DoorType.TIMED && openTicks == 0) openTicks = 200;
        return this;
    }
}

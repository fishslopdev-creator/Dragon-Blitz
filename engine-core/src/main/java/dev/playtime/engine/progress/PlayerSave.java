package dev.playtime.engine.progress;

import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything saved for one player in one world: {@code <world>/playtime/players/<uuid>.json}. */
public class PlayerSave {
    public static final int SCHEMA_VERSION = 1;

    public int schemaVersion = SCHEMA_VERSION;
    public UUID player;
    public String lastKnownName;
    /** True once the player has entered this world at least once (drives first-entry menus). */
    public boolean hasEnteredWorld;
    public int activeSlot;
    public List<SaveSlot> slots = new ArrayList<>();
    /** Explicitly assigned setup profile; {@code null} lets automatic assignment decide. */
    public Id assignedProfile;

    public SaveSlot slot(int index, int slotCount) {
        if (slots == null) slots = new ArrayList<>();
        while (slots.size() < slotCount) slots.add(new SaveSlot());
        int clamped = Math.max(0, Math.min(slotCount - 1, index));
        return slots.get(clamped);
    }

    public SaveSlot activeSlot(int slotCount) {
        activeSlot = Math.max(0, Math.min(slotCount - 1, activeSlot));
        return slot(activeSlot, slotCount);
    }
}

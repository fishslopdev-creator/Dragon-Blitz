package dev.playtime.engine.puzzle;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Saved state of one puzzle. {@code data} is template-owned scratch space. */
public class PuzzleState {
    public PuzzleStatus status = PuzzleStatus.INACTIVE;
    public long startedTick;
    /** Absolute tick at which a timed puzzle or stage fails (0 = no timer). */
    public long deadlineTick;
    public int attempts;
    public int stage;
    public Map<String, String> data = new LinkedHashMap<>();
    /** Players who contributed; all of them get credit on completion. */
    public Set<UUID> participants = new LinkedHashSet<>();
    public UUID completedBy;

    public PuzzleState normalize() {
        if (status == null) status = PuzzleStatus.INACTIVE;
        if (data == null) data = new LinkedHashMap<>();
        if (participants == null) participants = new LinkedHashSet<>();
        return this;
    }
}

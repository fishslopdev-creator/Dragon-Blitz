package dev.playtime.engine.puzzle;

import com.google.gson.JsonObject;
import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A placed puzzle: a template plus its settings. Everything is editable in JSON or the
 * Puzzle Controller GUI.
 */
public class PuzzleDefinition {
    public Id id;
    public String displayName;
    /** Template id such as {@code playtime:power_routing}. */
    public Id template;
    /** Template-specific settings (see each template's documentation). */
    public JsonObject settings = new JsonObject();
    public Set<Id> requiredItems = new LinkedHashSet<>();
    public Set<Id> requiredHands = new LinkedHashSet<>();
    /** The puzzle stays INACTIVE until these hold. */
    public List<Condition> activation = new ArrayList<>();
    /** Extra conditions checked when the template reports success. */
    public List<Condition> success = new ArrayList<>();
    /** Overall time limit once active (0 = none). */
    public int timeLimitTicks;
    /** Failures allowed before the puzzle stays FAILED (0 = unlimited). */
    public int maxAttempts;
    public ResetPolicy reset = ResetPolicy.ON_FAILURE;
    /** Whether a reset also clears linked device states and sockets. */
    public boolean resetDevices = true;
    /** Completion persists in saves. False for repeatable practice puzzles. */
    public boolean persistent = true;
    /** Extra devices whose events route to this puzzle (templates declare their own too). */
    public Set<Id> linkedDevices = new LinkedHashSet<>();
    /** Doors opened (and permanently unlocked for participants) on completion. */
    public Set<Id> linkedDoors = new LinkedHashSet<>();
    public List<Action> onStart = new ArrayList<>();
    public List<Action> onComplete = new ArrayList<>();
    public List<Action> onFail = new ArrayList<>();
    /** Rewards are onComplete actions shown in the editor separately (items, hands, keys). */
    public List<Action> rewards = new ArrayList<>();
    public Id completeSound = Id.of("puzzle.complete");
    public Id failSound = Id.of("puzzle.fail");
    public Id completeParticles;

    public PuzzleDefinition normalize() {
        if (settings == null) settings = new JsonObject();
        if (requiredItems == null) requiredItems = new LinkedHashSet<>();
        if (requiredHands == null) requiredHands = new LinkedHashSet<>();
        if (activation == null) activation = new ArrayList<>();
        if (success == null) success = new ArrayList<>();
        if (reset == null) reset = ResetPolicy.ON_FAILURE;
        if (linkedDevices == null) linkedDevices = new LinkedHashSet<>();
        if (linkedDoors == null) linkedDoors = new LinkedHashSet<>();
        if (onStart == null) onStart = new ArrayList<>();
        if (onComplete == null) onComplete = new ArrayList<>();
        if (onFail == null) onFail = new ArrayList<>();
        if (rewards == null) rewards = new ArrayList<>();
        if (displayName == null) displayName = id == null ? "Puzzle" : id.path();
        return this;
    }
}

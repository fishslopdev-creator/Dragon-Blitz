package dev.playtime.engine.chase;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Trigger;
import dev.playtime.engine.monster.RecoveryStrategy;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** A configurable chase sequence. Built in the chase editor or written as JSON. */
public class ChaseDefinition {
    public Id id;
    public String name;
    public Trigger trigger = new Trigger();
    public Id monster;
    public BlockRef spawn;
    public int startDelayTicks = 20;
    /** Default monster route for the whole chase. */
    public Id route;
    /** Overrides the monster's chase speed (blocks per tick). */
    public Double pursuitSpeed;
    public RecoveryStrategy recovery = RecoveryStrategy.TELEPORT;
    public List<Action> onStart = new ArrayList<>();
    public List<ChaseStage> stages = new ArrayList<>();
    public List<Action> onComplete = new ArrayList<>();
    public List<Action> onFail = new ArrayList<>();
    public Set<FailureOutcome> failure = EnumSet.of(FailureOutcome.RESPAWN_AT_CHECKPOINT, FailureOutcome.RESTART_CHASE);
    public Id checkpoint;
    public List<Id> resetPuzzles = new ArrayList<>();
    public boolean repeatable;
    /** Safety limit for the whole chase (0 = none); reaching it fails the chase. */
    public int maxDurationTicks = 20 * 60 * 5;
    public boolean despawnOnEnd = true;
    public Id music;

    public ChaseDefinition normalize() {
        if (name == null) name = id == null ? "Chase" : id.path();
        if (trigger == null) trigger = new Trigger();
        if (onStart == null) onStart = new ArrayList<>();
        if (stages == null) stages = new ArrayList<>();
        if (onComplete == null) onComplete = new ArrayList<>();
        if (onFail == null) onFail = new ArrayList<>();
        if (failure == null) failure = EnumSet.noneOf(FailureOutcome.class);
        if (resetPuzzles == null) resetPuzzles = new ArrayList<>();
        if (recovery == null) recovery = RecoveryStrategy.TELEPORT;
        int i = 0;
        for (ChaseStage s : stages) {
            if (s.id == null) s.id = "stage_" + (++i); else i++;
            if (s.success == null) s.success = new ArrayList<>();
            if (s.branches == null) s.branches = new ArrayList<>();
            if (s.onEnter == null) s.onEnter = new ArrayList<>();
            if (s.onSuccess == null) s.onSuccess = new ArrayList<>();
        }
        return this;
    }

    public int stageIndex(String stageId) {
        for (int i = 0; i < stages.size(); i++) if (stages.get(i).id.equals(stageId)) return i;
        return -1;
    }
}

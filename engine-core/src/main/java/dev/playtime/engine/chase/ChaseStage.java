package dev.playtime.engine.chase;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;

/** One step of a chase: an objective with a time limit and success conditions. */
public class ChaseStage {
    public String id;
    public String objective;
    /** 0 = no limit. */
    public int timeLimitTicks;
    /** All must hold to finish the stage. Empty means "finish immediately". */
    public List<Condition> success = new ArrayList<>();
    public List<ChaseBranch> branches = new ArrayList<>();
    /** Stage to go to on success; {@code null} = next in list; {@code "complete"} ends the chase. */
    public String next;
    public List<Action> onEnter = new ArrayList<>();
    public List<Action> onSuccess = new ArrayList<>();
    /** Respawn point while this stage is active (overrides the chase checkpoint). */
    public Id checkpoint;
    /** Waypoints for the monster during this stage (overrides the chase route). */
    public Id monsterRoute;
    public Double monsterSpeed;
}

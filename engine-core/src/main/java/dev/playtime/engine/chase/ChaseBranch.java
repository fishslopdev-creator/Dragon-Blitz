package dev.playtime.engine.chase;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional fork out of a stage. Checked before the stage's own success conditions.
 * {@code next} is a stage id, or {@code "complete"} / {@code "fail"}.
 */
public class ChaseBranch {
    public List<Condition> conditions = new ArrayList<>();
    public String next;
    public List<Action> actions = new ArrayList<>();
}

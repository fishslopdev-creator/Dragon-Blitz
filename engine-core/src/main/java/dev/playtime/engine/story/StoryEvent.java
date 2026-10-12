package dev.playtime.engine.story;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.logic.Trigger;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;

/** A trigger plus a list of actions. The building block of chapters. */
public class StoryEvent {
    public Id id;
    public String name;
    public Trigger trigger = new Trigger();
    public List<Condition> conditions = new ArrayList<>();
    public List<Action> actions = new ArrayList<>();
    public Repeat repeat = Repeat.ONCE_PER_SAVE;
    public int cooldownTicks;

    public StoryEvent normalize() {
        if (trigger == null) trigger = new Trigger();
        if (conditions == null) conditions = new ArrayList<>();
        if (actions == null) actions = new ArrayList<>();
        if (repeat == null) repeat = Repeat.ONCE_PER_SAVE;
        if (name == null) name = id == null ? "event" : id.path();
        return this;
    }
}

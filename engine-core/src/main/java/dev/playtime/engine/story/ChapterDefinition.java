package dev.playtime.engine.story;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;

public class ChapterDefinition {
    public Id id;
    public String name;
    public String subtitle = "";
    public int order;
    public Id startCheckpoint;
    public Id next;
    /** Shown in the Chapters menu only once reached (or always when false). */
    public boolean hiddenUntilReached = true;
    public List<Action> onStart = new ArrayList<>();
    public List<Action> onComplete = new ArrayList<>();
}

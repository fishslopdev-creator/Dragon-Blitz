package dev.playtime.engine.story;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Branching dialogue shown on the dialogue screen. */
public class DialogueTree {
    public Id id;
    public String start = "start";
    public Map<String, Node> nodes = new LinkedHashMap<>();

    public static class Node {
        public String speaker;
        public Id portrait;
        public String text = "";
        public Id voice;
        public List<Option> options = new ArrayList<>();
        /** Automatic next node when there are no options; {@code null} ends the dialogue. */
        public String next;
        public List<Action> actions = new ArrayList<>();
    }

    public static class Option {
        public String text;
        public String next;
        public List<Condition> conditions = new ArrayList<>();
        public List<Action> actions = new ArrayList<>();
    }
}

package dev.playtime.engine.logic;

import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One configurable effect, for example
 * {@code {"type": "door_open", "id": "playtime:security_door"}} or
 * {@code {"type": "title_card", "text": "Chapter 1", "subtitle": "A Tight Squeeze", "ticks": 80}}.
 * Unused fields are ignored for a given type.
 */
public class Action {
    public ActionType type;
    public Id id;
    public String text;
    public String subtitle;
    /** Duration in ticks (title cards, lights, fades). */
    public int ticks;
    /** Delay in ticks before the action runs. */
    public int delay;
    public int value;
    public boolean on = true;
    public BlockRef pos;
    public Box area;
    public Id sound;
    public float volume = 1.0f;
    public Map<String, String> params = new LinkedHashMap<>();

    public static Action of(ActionType type, Id id) {
        Action a = new Action();
        a.type = type;
        a.id = id;
        return a;
    }

    public String param(String key, String fallback) {
        String v = params == null ? null : params.get(key);
        return v == null ? fallback : v;
    }
}

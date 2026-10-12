package dev.playtime.engine.logic;

import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.Id;

/** Configurable start condition: a type plus the id/area it watches. */
public class Trigger {
    public TriggerType type = TriggerType.MANUAL;
    /** Watched id: puzzle, door, device, item, dialogue, chapter or hand depending on {@link #type}. */
    public Id id;
    public Box area;
    /** Delay for {@link TriggerType#TIMER}, counted from when the trigger was armed. */
    public int ticks;

    public static Trigger of(TriggerType type, Id id) {
        Trigger t = new Trigger();
        t.type = type;
        t.id = id;
        return t;
    }

    public boolean matches(TriggerType eventType, Id eventId) {
        return type == eventType && (id == null || id.equals(eventId));
    }
}

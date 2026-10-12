package dev.playtime.engine.logic;

import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;

/**
 * A JSON-configurable test, for example
 * {@code {"type": "puzzle_completed", "id": "playtime:assembly_line"}} or
 * {@code {"type": "all", "of": [ ... ]}}.
 */
public class Condition {
    public ConditionType type = ConditionType.ALWAYS;
    public Id id;
    public List<Condition> of = new ArrayList<>();
    public Condition condition;
    public Box area;

    public static Condition of(ConditionType type, Id id) {
        Condition c = new Condition();
        c.type = type;
        c.id = id;
        return c;
    }

    public static Condition all(List<Condition> list) {
        Condition c = new Condition();
        c.type = ConditionType.ALL;
        c.of = new ArrayList<>(list);
        return c;
    }

    public static Condition not(Condition inner) {
        Condition c = new Condition();
        c.type = ConditionType.NOT;
        c.condition = inner;
        return c;
    }

    /** Evaluates with a depth limit so a hand-written self-referencing structure cannot overflow the stack. */
    public boolean test(Facts facts) {
        return test(facts, 0);
    }

    private boolean test(Facts f, int depth) {
        if (depth > 32 || type == null) return false;
        return switch (type) {
            case ALWAYS -> true;
            case ALL -> {
                if (of != null) for (Condition c : of) if (c != null && !c.test(f, depth + 1)) yield false;
                yield true;
            }
            case ANY -> {
                if (of == null || of.isEmpty()) yield true;
                for (Condition c : of) if (c != null && c.test(f, depth + 1)) yield true;
                yield false;
            }
            case NOT -> condition != null && !condition.test(f, depth + 1);
            case FLAG -> id != null && f.flag(id);
            case WORLD_FLAG -> id != null && f.worldFlag(id);
            case HAS_HAND -> id != null && f.hasHand(id);
            case SELECTED_HAND -> id != null && f.selectedHand(id);
            case HAS_KEY -> id != null && f.hasKey(id);
            case PUZZLE_COMPLETED -> id != null && f.puzzleCompleted(id);
            case EVENT_TRIGGERED -> id != null && f.eventTriggered(id);
            case OBJECTIVE_COMPLETED -> id != null && f.objectiveCompleted(id);
            case OBJECTIVE_ACTIVE -> id != null && f.objectiveActive(id);
            case CHAPTER -> id != null && f.chapterIs(id);
            case DEVICE_POWERED -> id != null && f.devicePowered(id);
            case DEVICE_ACTIVE -> id != null && f.deviceActive(id);
            case DOOR_OPEN -> id != null && f.doorOpen(id);
            case CHASE_COMPLETED -> id != null && f.chaseCompleted(id);
            case COLLECTIBLE -> id != null && f.hasCollectible(id);
            case IN_AREA -> area != null && f.inArea(area);
        };
    }

    /** Collects every identifier this condition refers to, for map validation. */
    public void collectReferences(List<Condition> out) {
        out.add(this);
        if (of != null) for (Condition c : of) if (c != null) c.collectReferences(out);
        if (condition != null) condition.collectReferences(out);
    }

    public static boolean allMatch(List<Condition> conditions, Facts facts) {
        if (conditions == null) return true;
        for (Condition c : conditions) if (c != null && !c.test(facts)) return false;
        return true;
    }
}

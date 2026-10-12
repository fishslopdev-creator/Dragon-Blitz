package dev.playtime.engine.graph;

import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;

/** A saved link made with the Connection Device. */
public class Connection {
    public Id id;
    public String name;
    public Id from;
    public Id to;
    public ConnectionType type = ConnectionType.ACTIVATE;
    public int delayTicks;
    /** Swap ON and OFF before delivery. */
    public boolean invert;
    public boolean enabled = true;
    public List<Condition> conditions = new ArrayList<>();
    /** For START_CHASE / TRIGGER_EVENT / RESET_PUZZLE / *_DOOR links: the chase, event, puzzle or door id. */
    public Id targetRef;

    public String displayName() {
        return name != null && !name.isBlank() ? name : String.valueOf(id);
    }
}

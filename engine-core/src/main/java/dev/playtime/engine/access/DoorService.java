package dev.playtime.engine.access;

import dev.playtime.engine.util.Id;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Owns every door's definition and saved state. Timed doors are closed from a
 * priority queue keyed by close tick, so idle doors cost nothing per tick.
 */
public final class DoorService {
    private final Map<Id, DoorDefinition> doors = new LinkedHashMap<>();
    private final Map<Id, DoorState> states;
    private final PriorityQueue<Pending> closeQueue = new PriorityQueue<>();
    private DoorListener listener = (d, o) -> {};

    private record Pending(long tick, Id door) implements Comparable<Pending> {
        @Override
        public int compareTo(Pending o) {
            return Long.compare(tick, o.tick);
        }
    }

    /** @param savedStates live map from world state; modified in place */
    public DoorService(Map<Id, DoorState> savedStates) {
        this.states = savedStates;
    }

    public void setListener(DoorListener listener) {
        this.listener = listener == null ? (d, o) -> {} : listener;
    }

    public void register(DoorDefinition def) {
        def.normalize();
        doors.put(def.id, def);
        DoorState state = states.get(def.id);
        if (state == null) {
            state = new DoorState();
            state.open = def.startsOpen;
            states.put(def.id, state);
        } else if (state.closeAtTick > 0) {
            closeQueue.add(new Pending(state.closeAtTick, def.id));
        }
    }

    public boolean unregister(Id id) {
        states.remove(id);
        return doors.remove(id) != null;
    }

    public Optional<DoorDefinition> get(Id id) {
        return Optional.ofNullable(id == null ? null : doors.get(id));
    }

    public Collection<DoorDefinition> all() {
        return Collections.unmodifiableCollection(doors.values());
    }

    public DoorState state(Id id) {
        return states.computeIfAbsent(id, k -> new DoorState());
    }

    public boolean isOpen(Id id) {
        DoorState s = states.get(id);
        return s != null && s.open;
    }

    /**
     * Opens the door if it is not force-locked. Returns false when nothing changed.
     * {@code tick} schedules the auto-close for timed doors.
     */
    public boolean open(Id id, long tick) {
        DoorDefinition def = doors.get(id);
        if (def == null) return false;
        DoorState s = state(id);
        if (s.forcedLocked) return false;
        if (def.openTicks > 0) {
            s.closeAtTick = tick + def.openTicks;
            closeQueue.add(new Pending(s.closeAtTick, id));
        }
        if (s.open) return false;
        s.open = true;
        listener.doorChanged(def, true);
        return true;
    }

    public boolean close(Id id) {
        DoorDefinition def = doors.get(id);
        if (def == null) return false;
        DoorState s = state(id);
        s.closeAtTick = 0;
        if (!s.open) return false;
        s.open = false;
        listener.doorChanged(def, false);
        return true;
    }

    public void setForcedLock(Id id, boolean locked) {
        DoorState s = state(id);
        s.forcedLocked = locked;
        if (locked) close(id);
    }

    /** Applies power to POWER-operated doors; the caller passes whether the rule's conditions hold. */
    public void onPowerChanged(Id id, boolean powered, boolean conditionsHold, long tick) {
        DoorDefinition def = doors.get(id);
        if (def == null || def.effectiveOperation() != DoorOperation.POWER) return;
        if (powered && conditionsHold) open(id, tick); else close(id);
    }

    public void tick(long tick) {
        while (!closeQueue.isEmpty() && closeQueue.peek().tick <= tick) {
            Pending p = closeQueue.poll();
            DoorState s = states.get(p.door);
            // Stale entries (door re-opened later, or closed manually) are skipped.
            if (s != null && s.closeAtTick == p.tick) close(p.door);
        }
    }
}

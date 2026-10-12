package dev.playtime.engine.engine;

import dev.playtime.engine.access.DoorState;
import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceState;
import dev.playtime.engine.graph.ScheduledSignal;
import dev.playtime.engine.progress.SaveSlot;
import dev.playtime.engine.puzzle.PuzzleState;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runtime state of one world: {@code <world>/playtime/world_state.json}. */
public class WorldState {
    public static final int SCHEMA_VERSION = 1;

    public int schemaVersion = SCHEMA_VERSION;
    /** Story save used by SHARED and HOST_OWNED progression. */
    public SaveSlot sharedSlot = new SaveSlot();
    public Map<Id, Device> devices = new LinkedHashMap<>();
    public Map<Id, DeviceState> deviceStates = new LinkedHashMap<>();
    public List<Connection> connections = new ArrayList<>();
    public List<ScheduledSignal> scheduledSignals = new ArrayList<>();
    public List<ScheduledAction> scheduledActions = new ArrayList<>();
    public Map<Id, DoorState> doors = new LinkedHashMap<>();
    public Map<Id, PuzzleState> puzzles = new LinkedHashMap<>();
    public Set<Id> firedEvents = new LinkedHashSet<>();
    public Set<Id> completedChases = new LinkedHashSet<>();
    public Set<Id> worldFlags = new LinkedHashSet<>();

    public WorldState normalize() {
        if (sharedSlot == null) sharedSlot = new SaveSlot();
        if (sharedSlot.progress != null) sharedSlot.progress.normalize();
        if (devices == null) devices = new LinkedHashMap<>();
        if (deviceStates == null) deviceStates = new LinkedHashMap<>();
        deviceStates.values().removeIf(java.util.Objects::isNull);
        for (DeviceState s : deviceStates.values()) {
            if (s.activeInputs == null) s.activeInputs = new LinkedHashSet<>();
            if (s.sockets == null) s.sockets = new LinkedHashMap<>();
        }
        if (connections == null) connections = new ArrayList<>();
        if (scheduledSignals == null) scheduledSignals = new ArrayList<>();
        if (scheduledActions == null) scheduledActions = new ArrayList<>();
        if (doors == null) doors = new LinkedHashMap<>();
        if (puzzles == null) puzzles = new LinkedHashMap<>();
        if (firedEvents == null) firedEvents = new LinkedHashSet<>();
        if (completedChases == null) completedChases = new LinkedHashSet<>();
        if (worldFlags == null) worldFlags = new LinkedHashSet<>();
        return this;
    }
}

package dev.playtime.engine.graph;

import dev.playtime.engine.util.Id;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Saved runtime state of a device. */
public class DeviceState {
    /** Switched on / pressed / activated. */
    public boolean active;
    /** Receives power (computed by the power network; saved for clients and reloads). */
    public boolean powered;
    /** Disabled devices ignore players and signals until enabled again. */
    public boolean disabled;
    /** Source devices currently holding this device's inputs high. */
    public Set<Id> activeInputs = new LinkedHashSet<>();
    /** Items inserted into sockets (fuses, machine parts) keyed by socket name. */
    public Map<String, Id> sockets = new LinkedHashMap<>();
    /** Tick until which this device is temporarily powered (Green Hand charge, Pulse Hand). */
    public long tempPowerUntil;
}

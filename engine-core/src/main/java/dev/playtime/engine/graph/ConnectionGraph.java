package dev.playtime.engine.graph;

import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Devices and the links between them, indexed by id, by position and by direction so
 * that signal delivery and power solving never scan the whole map.
 */
public final class ConnectionGraph {
    private final Map<Id, Device> devices = new LinkedHashMap<>();
    private final Map<Id, DeviceState> states;
    private final Map<Id, Connection> connections = new LinkedHashMap<>();
    private final Map<Id, List<Connection>> outgoing = new HashMap<>();
    private final Map<Id, List<Connection>> incoming = new HashMap<>();
    private final Map<BlockRef, Id> byPos = new HashMap<>();
    private int nextConnection = 1;

    /** @param savedStates live map from world state, modified in place */
    public ConnectionGraph(Map<Id, DeviceState> savedStates) {
        this.states = savedStates;
    }

    // ------------------------------------------------------------------ devices

    public void addDevice(Device device) {
        Device old = devices.put(device.id, device);
        if (old != null && old.pos != null) byPos.remove(old.pos);
        if (device.pos != null) byPos.put(device.pos, device.id);
        states.computeIfAbsent(device.id, k -> new DeviceState());
    }

    /** Removes a device and every connection touching it (block broken). */
    public List<Connection> removeDevice(Id id) {
        Device d = devices.remove(id);
        if (d == null) return List.of();
        if (d.pos != null) byPos.remove(d.pos);
        states.remove(id);
        List<Connection> removed = new ArrayList<>();
        for (Connection c : List.copyOf(connections.values())) {
            if (c.from.equals(id) || c.to.equals(id)) {
                removeConnection(c.id);
                removed.add(c);
            }
        }
        for (DeviceState s : states.values()) s.activeInputs.remove(id);
        return removed;
    }

    public Optional<Device> device(Id id) {
        return Optional.ofNullable(id == null ? null : devices.get(id));
    }

    public Optional<Device> deviceAt(BlockRef pos) {
        return device(byPos.get(pos));
    }

    public DeviceState state(Id id) {
        return states.computeIfAbsent(id, k -> new DeviceState());
    }

    public Collection<Device> devices() {
        return Collections.unmodifiableCollection(devices.values());
    }

    public List<Device> devicesOfType(DeviceType type) {
        List<Device> out = new ArrayList<>();
        for (Device d : devices.values()) if (d.type == type) out.add(d);
        return out;
    }

    /** Next free id such as {@code playtime:generator_3} for a newly placed block. */
    public Id freshDeviceId(String namespace, String base) {
        for (int i = 1; ; i++) {
            Id candidate = new Id(namespace, base + "_" + i);
            if (!devices.containsKey(candidate)) return candidate;
        }
    }

    // -------------------------------------------------------------- connections

    public Collection<Connection> connections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    public Optional<Connection> connection(Id id) {
        return Optional.ofNullable(connections.get(id));
    }

    public List<Connection> outgoing(Id device) {
        return outgoing.getOrDefault(device, List.of());
    }

    public List<Connection> incoming(Id device) {
        return incoming.getOrDefault(device, List.of());
    }

    /**
     * Validates and adds a connection. Returns the issues found; the connection is only
     * added when there are no errors.
     */
    public IssueList connect(Connection c) {
        IssueList issues = validate(c);
        if (issues.hasErrors()) return issues;
        if (c.id == null) c.id = freshConnectionId();
        connections.put(c.id, c);
        outgoing.computeIfAbsent(c.from, k -> new ArrayList<>()).add(c);
        incoming.computeIfAbsent(c.to, k -> new ArrayList<>()).add(c);
        return issues;
    }

    /** Loads a saved connection without loop checks (they were checked when it was made). */
    public void restore(Connection c) {
        if (c == null || c.id == null || c.from == null || c.to == null) return;
        connections.put(c.id, c);
        outgoing.computeIfAbsent(c.from, k -> new ArrayList<>()).add(c);
        incoming.computeIfAbsent(c.to, k -> new ArrayList<>()).add(c);
        bumpCounter(c.id);
    }

    public boolean removeConnection(Id id) {
        Connection c = connections.remove(id);
        if (c == null) return false;
        List<Connection> out = outgoing.get(c.from);
        if (out != null) out.remove(c);
        List<Connection> in = incoming.get(c.to);
        if (in != null) in.remove(c);
        DeviceState target = states.get(c.to);
        if (target != null) target.activeInputs.remove(c.from);
        return true;
    }

    public boolean rename(Id id, String name) {
        Connection c = connections.get(id);
        if (c == null) return false;
        c.name = name;
        return true;
    }

    public IssueList validate(Connection c) {
        IssueList issues = new IssueList();
        String src = "connection " + (c.id == null ? "(new)" : c.id);
        if (c.from == null || c.to == null || c.type == null) {
            issues.error(src, "needs a source, a destination and a type");
            return issues;
        }
        Device from = devices.get(c.from);
        Device to = devices.get(c.to);
        if (from == null) issues.error(src, "source device " + c.from + " does not exist");
        if (to == null) issues.error(src, "destination device " + c.to + " does not exist");
        if (c.from.equals(c.to)) issues.error(src, "a device cannot be connected to itself");
        if (c.id != null && connections.containsKey(c.id)) issues.error(src, "id already in use");
        if (issues.hasErrors()) return issues;
        if (c.delayTicks < 0) issues.error(src, "delay cannot be negative");
        if (c.type == ConnectionType.POWER && (!from.type.conductsPower() || !to.type.conductsPower())) {
            issues.error(src, "power links need two powered devices (" + from.type + " -> " + to.type + ")");
        }
        for (Connection existing : outgoing(c.from)) {
            if (existing.to.equals(c.to) && existing.type == c.type) {
                issues.error(src, "an identical link already exists (" + existing.id + ")");
            }
        }
        if (c.type.propagates() && c.delayTicks == 0 && reachesInstantly(c.to, c.from)) {
            issues.error(src, "this link would create an instant loop between " + c.from + " and " + c.to
                    + "; add a delay or remove a link in the loop");
        }
        if (c.type == ConnectionType.POWER && reachesByPower(c.to, c.from)) {
            issues.info(src, "power loop detected; harmless, power simply flows both ways");
        }
        return issues;
    }

    private boolean reachesInstantly(Id start, Id goal) {
        Set<Id> seen = new HashSet<>();
        Deque<Id> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            Id cur = queue.poll();
            if (cur.equals(goal)) return true;
            if (!seen.add(cur)) continue;
            for (Connection c : outgoing(cur)) {
                if (c.enabled && c.type.propagates() && c.delayTicks == 0) queue.add(c.to);
            }
        }
        return false;
    }

    private boolean reachesByPower(Id start, Id goal) {
        Set<Id> seen = new HashSet<>();
        Deque<Id> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            Id cur = queue.poll();
            if (cur.equals(goal)) return true;
            if (!seen.add(cur)) continue;
            for (Connection c : outgoing(cur)) if (c.type == ConnectionType.POWER) queue.add(c.to);
        }
        return false;
    }

    private Id freshConnectionId() {
        Id id;
        do {
            id = Id.of("link_" + nextConnection++);
        } while (connections.containsKey(id));
        return id;
    }

    private void bumpCounter(Id id) {
        String path = id.path();
        if (path.startsWith("link_")) {
            try {
                nextConnection = Math.max(nextConnection, Integer.parseInt(path.substring(5)) + 1);
            } catch (NumberFormatException ignored) {
                // custom name
            }
        }
    }
}

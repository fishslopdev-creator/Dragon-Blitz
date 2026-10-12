package dev.playtime.engine.power;

import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.ConnectionGraph;
import dev.playtime.engine.graph.ConnectionType;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceState;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.util.Id;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solves which devices receive power. Power links are treated as undirected wires.
 * Power passes through a device unless it is blocking: an open switch, a fuse box or
 * socketed device missing parts, a node that needs activating, or a disabled device.
 *
 * <p>Within each connected circuit, consumers are served in priority order until the
 * circuit's supply runs out. Recomputation is requested by marking the network dirty and
 * runs at most once per tick.
 *
 * <p>Device settings understood here:
 * <ul>
 *   <li>{@code capacity} (sources; default 10)</li>
 *   <li>{@code demand} and {@code priority} (consumers; default 1 and 0)</li>
 *   <li>{@code sockets}: {@code slot=item,slot=item} parts required before the device passes power</li>
 *   <li>{@code needs_activation}: {@code true} makes a power node block until activated</li>
 *   <li>{@code trip_on_overload}: {@code true} switches a generator off when demand exceeds supply</li>
 *   <li>{@code temp_capacity}: supply while temporarily charged (default 2)</li>
 * </ul>
 */
public final class PowerNetwork {
    private final ConnectionGraph graph;
    private boolean dirty = true;
    /** Earliest pending temporary-power expiry, so expiries need no per-tick scan. */
    private long nextExpiry = Long.MAX_VALUE;

    public PowerNetwork(ConnectionGraph graph) {
        this.graph = graph;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isPowered(Id device) {
        return graph.device(device).isPresent() && graph.state(device).powered;
    }

    /** Recomputes if dirty or if temporary power expired. */
    public PowerResult update(long tick) {
        if (!dirty && tick < nextExpiry) {
            return new PowerResult(Set.of(), Set.of(), Set.of());
        }
        dirty = false;
        return recompute(tick);
    }

    public PowerResult recompute(long tick) {
        Map<Id, Boolean> before = new LinkedHashMap<>();
        for (Device d : graph.devices()) before.put(d.id, graph.state(d.id).powered);

        Set<Id> powered = new HashSet<>();
        Set<Id> tripped = new LinkedHashSet<>();
        Set<Id> visited = new HashSet<>();
        for (Device d : graph.devices()) {
            if (visited.contains(d.id) || !d.type.conductsPower()) continue;
            List<Device> circuit = collectCircuit(d, visited);
            solveCircuit(circuit, tick, powered, tripped);
        }

        nextExpiry = Long.MAX_VALUE;
        for (Device d : graph.devices()) {
            long until = graph.state(d.id).tempPowerUntil;
            if (until > tick) nextExpiry = Math.min(nextExpiry, until);
        }

        Set<Id> on = new LinkedHashSet<>();
        Set<Id> off = new LinkedHashSet<>();
        for (Device d : graph.devices()) {
            DeviceState s = graph.state(d.id);
            boolean now = powered.contains(d.id);
            if (now != Boolean.TRUE.equals(before.get(d.id))) {
                (now ? on : off).add(d.id);
            }
            s.powered = now;
        }
        if (!tripped.isEmpty()) dirty = true;
        return new PowerResult(on, off, tripped);
    }

    /** True when a device passes power through itself. */
    public boolean passes(Device d, long tick) {
        DeviceState s = graph.state(d.id);
        if (s.disabled) return false;
        if (d.type == DeviceType.POWER_SWITCH && !s.active) return false;
        if (d.type == DeviceType.POWER_NODE && "true".equals(d.setting("needs_activation", "false"))
                && !s.active && s.tempPowerUntil <= tick) return false;
        return socketsSatisfied(d, s);
    }

    public static boolean socketsSatisfied(Device d, DeviceState s) {
        Map<String, Id> required = requiredSockets(d);
        for (Map.Entry<String, Id> e : required.entrySet()) {
            if (!e.getValue().equals(s.sockets.get(e.getKey()))) return false;
        }
        return true;
    }

    /** Parses the {@code sockets} setting ({@code slot=item,slot=item}). */
    public static Map<String, Id> requiredSockets(Device d) {
        Map<String, Id> out = new LinkedHashMap<>();
        String raw = d.setting("sockets", "");
        if (raw.isBlank()) return out;
        for (String part : raw.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            Id item = Id.tryParse(part.substring(eq + 1).trim());
            if (item != null) out.put(part.substring(0, eq).trim(), item);
        }
        return out;
    }

    private List<Device> collectCircuit(Device start, Set<Id> visited) {
        List<Device> circuit = new ArrayList<>();
        Deque<Device> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start.id);
        while (!queue.isEmpty()) {
            Device cur = queue.poll();
            circuit.add(cur);
            for (Device next : powerNeighbours(cur)) {
                if (visited.add(next.id)) queue.add(next);
            }
        }
        return circuit;
    }

    private List<Device> powerNeighbours(Device d) {
        List<Device> out = new ArrayList<>();
        for (Connection c : graph.outgoing(d.id)) {
            if (c.type == ConnectionType.POWER && c.enabled) graph.device(c.to).ifPresent(out::add);
        }
        for (Connection c : graph.incoming(d.id)) {
            if (c.type == ConnectionType.POWER && c.enabled) graph.device(c.from).ifPresent(out::add);
        }
        return out;
    }

    /**
     * Within a wired circuit, power only reaches devices connected to a source through
     * passing devices. Blocking devices can still be reached (so a switch shows it has
     * power at its input) but do not forward it.
     */
    private void solveCircuit(List<Device> circuit, long tick, Set<Id> powered, Set<Id> tripped) {
        Set<Id> reached = new HashSet<>();
        Deque<Device> queue = new ArrayDeque<>();
        int supply = 0;
        List<Device> sources = new ArrayList<>();
        for (Device d : circuit) {
            int cap = sourceCapacity(d, tick);
            if (cap > 0) {
                supply += cap;
                sources.add(d);
                if (reached.add(d.id)) queue.add(d);
            }
        }
        if (supply <= 0) return;
        while (!queue.isEmpty()) {
            Device cur = queue.poll();
            if (!passes(cur, tick)) continue;
            for (Device next : powerNeighbours(cur)) {
                if (reached.add(next.id)) queue.add(next);
            }
        }
        List<Device> consumers = new ArrayList<>();
        int totalDemand = 0;
        for (Device d : circuit) {
            if (!reached.contains(d.id)) continue;
            if (isConsumer(d)) {
                consumers.add(d);
                totalDemand += Math.max(0, d.intSetting("demand", 1));
            } else {
                powered.add(d.id);
            }
        }
        if (totalDemand > supply) {
            for (Device src : sources) {
                if (src.type == DeviceType.GENERATOR && "true".equals(src.setting("trip_on_overload", "false"))) {
                    graph.state(src.id).active = false;
                    tripped.add(src.id);
                }
            }
            if (!tripped.isEmpty()) {
                // A tripped generator stops supplying; the next recompute settles the circuit.
                for (Device d : circuit) powered.remove(d.id);
                return;
            }
        }
        consumers.sort(Comparator.comparingInt((Device d) -> -d.intSetting("priority", 0)).thenComparing(d -> d.id));
        int remaining = supply;
        for (Device d : consumers) {
            int demand = Math.max(0, d.intSetting("demand", 1));
            if (demand <= remaining) {
                remaining -= demand;
                if (socketsSatisfied(d, graph.state(d.id)) && !graph.state(d.id).disabled) powered.add(d.id);
            }
        }
    }

    private int sourceCapacity(Device d, long tick) {
        DeviceState s = graph.state(d.id);
        if (s.disabled) return 0;
        int cap = 0;
        boolean isSource = d.type == DeviceType.GENERATOR || d.type == DeviceType.BATTERY_DEVICE
                || d.type == DeviceType.CHARGING_STATION;
        if (isSource && s.active && socketsSatisfied(d, s)) cap += Math.max(0, d.intSetting("capacity", 10));
        if (s.tempPowerUntil > tick) cap += Math.max(1, d.intSetting("temp_capacity", 2));
        return cap;
    }

    private static boolean isConsumer(Device d) {
        return switch (d.type) {
            case GENERATOR, BATTERY_DEVICE, CHARGING_STATION, POWER_NODE, POWER_SWITCH, FUSE_BOX -> false;
            default -> true;
        };
    }

    /** Temporarily powers a device (Green Hand charge, Pulse Hand) until {@code untilTick}. */
    public void chargeTemporarily(Id device, long untilTick) {
        DeviceState s = graph.state(device);
        s.tempPowerUntil = Math.max(s.tempPowerUntil, untilTick);
        nextExpiry = Math.min(nextExpiry, untilTick);
        dirty = true;
    }
}

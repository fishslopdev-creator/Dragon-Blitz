package dev.playtime.engine.graph;

import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Propagates signals along connections. Guards: a depth limit per chain and a
 * per-tick delivery budget, so a misconfigured map degrades to a warning instead
 * of a frozen server.
 */
public final class SignalBus {
    private static final Logger LOG = Logger.getLogger("PlaytimeEngine");
    public static final int MAX_DEPTH = 32;
    public static final int MAX_DELIVERIES_PER_TICK = 4096;

    private final ConnectionGraph graph;
    private final SignalHandler handler;
    private final PriorityQueue<ScheduledSignal> scheduled =
            new PriorityQueue<>((a, b) -> Long.compare(a.tick(), b.tick()));
    private long currentTick;
    private int deliveriesThisTick;
    private boolean budgetWarned;

    public SignalBus(ConnectionGraph graph, SignalHandler handler) {
        this.graph = graph;
        this.handler = handler;
    }

    public ConnectionGraph graph() {
        return graph;
    }

    /** Player-driven or engine-driven state change of a device (switch flipped, button pressed). */
    public void setActive(Id deviceId, boolean active, UUID player) {
        Device d = graph.device(deviceId).orElse(null);
        if (d == null) return;
        DeviceState s = graph.state(deviceId);
        if (s.disabled || s.active == active) return;
        s.active = active;
        handler.deviceChanged(d, active, player);
        emit(deviceId, active ? Signal.ON : Signal.OFF, player, 0);
    }

    public void pulse(Id deviceId, UUID player) {
        Device d = graph.device(deviceId).orElse(null);
        if (d == null || graph.state(deviceId).disabled) return;
        handler.devicePulsed(d, player);
        emit(deviceId, Signal.PULSE, player, 0);
    }

    public void emit(Id source, Signal signal, UUID player) {
        emit(source, signal, player, 0);
    }

    private void emit(Id source, Signal signal, UUID player, int depth) {
        if (depth > MAX_DEPTH) {
            LOG.warning("Signal chain from " + source + " exceeded " + MAX_DEPTH + " steps; stopping it. Check for loops.");
            return;
        }
        for (Connection c : List.copyOf(graph.outgoing(source))) {
            if (!c.enabled || c.type == ConnectionType.POWER) continue;
            if (!Condition.allMatch(c.conditions, handler.facts(player))) continue;
            if (c.delayTicks > 0) {
                scheduled.add(new ScheduledSignal(currentTick + c.delayTicks, c.id, signal, player));
            } else {
                deliver(c, signal, player, depth + 1);
            }
        }
    }

    private void deliver(Connection c, Signal raw, UUID player, int depth) {
        if (++deliveriesThisTick > MAX_DELIVERIES_PER_TICK) {
            if (!budgetWarned) {
                LOG.warning("Signal budget exhausted this tick; dropping further deliveries (last: " + c.id + ")");
                budgetWarned = true;
            }
            return;
        }
        Device target = graph.device(c.to).orElse(null);
        if (target == null) return;
        Signal signal = c.invert ? invert(raw) : raw;
        DeviceState s = graph.state(target.id);
        switch (c.type) {
            case ACTIVATE -> {
                if (s.disabled) return;
                if (signal == Signal.PULSE) {
                    if (target.latch) {
                        changeActive(target, s, true, player, depth);
                    } else {
                        handler.devicePulsed(target, player);
                        emit(target.id, Signal.PULSE, player, depth);
                    }
                    return;
                }
                if (signal == Signal.ON) s.activeInputs.add(c.from); else s.activeInputs.remove(c.from);
                boolean nowActive = s.activeInputs.size() >= Math.max(1, target.inputsRequired) || target.latch && s.active;
                changeActive(target, s, nowActive, player, depth);
            }
            case DEACTIVATE -> {
                if (!s.disabled && signal != Signal.OFF) changeActive(target, s, false, player, depth);
            }
            case TOGGLE -> {
                if (!s.disabled && signal != Signal.OFF) changeActive(target, s, !s.active, player, depth);
            }
            case ENABLE -> s.disabled = signal == Signal.OFF;
            case DISABLE -> s.disabled = signal != Signal.OFF;
            default -> handler.deliver(c, target, signal, player);
        }
    }

    private void changeActive(Device target, DeviceState s, boolean active, UUID player, int depth) {
        if (s.active == active) return;
        s.active = active;
        handler.deviceChanged(target, active, player);
        emit(target.id, active ? Signal.ON : Signal.OFF, player, depth);
    }

    private static Signal invert(Signal s) {
        return switch (s) {
            case ON -> Signal.OFF;
            case OFF -> Signal.ON;
            case PULSE -> Signal.PULSE;
        };
    }

    public void tick(long tick) {
        currentTick = tick;
        deliveriesThisTick = 0;
        budgetWarned = false;
        while (!scheduled.isEmpty() && scheduled.peek().tick() <= tick) {
            ScheduledSignal next = scheduled.poll();
            graph.connection(next.connection()).ifPresent(c -> {
                if (c.enabled) deliver(c, next.signal(), next.player(), 1);
            });
        }
    }

    public void setTick(long tick) {
        currentTick = tick;
    }

    public List<ScheduledSignal> pendingSnapshot() {
        return new ArrayList<>(scheduled);
    }

    public void restorePending(List<ScheduledSignal> pending) {
        scheduled.clear();
        if (pending != null) for (ScheduledSignal s : pending) if (s != null && s.connection() != null) scheduled.add(s);
    }
}

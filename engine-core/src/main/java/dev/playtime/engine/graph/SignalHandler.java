package dev.playtime.engine.graph;

import dev.playtime.engine.logic.Facts;

import java.util.UUID;

/** Engine hooks for signal effects that live outside the graph (doors, chases, events, puzzles). */
public interface SignalHandler {
    /** Delivers door/chase/event/puzzle links. */
    void deliver(Connection connection, Device target, Signal signal, UUID player);

    /** A device's active state changed (animate the block, recompute power, notify puzzles). */
    void deviceChanged(Device device, boolean active, UUID player);

    /** A device received a momentary pulse. */
    void devicePulsed(Device device, UUID player);

    /** Facts used to evaluate link conditions; {@code player} may be null for world-level signals. */
    Facts facts(UUID player);
}

package dev.playtime.engine.graph;

import dev.playtime.engine.util.Id;

import java.util.UUID;

/** A delayed delivery, saved with the world so timers survive restarts. */
public record ScheduledSignal(long tick, Id connection, Signal signal, UUID player) {}

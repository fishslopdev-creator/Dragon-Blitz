package dev.playtime.engine.engine;

import dev.playtime.engine.logic.Action;

import java.util.UUID;

/** A delayed action, saved so long timers survive restarts. */
public record ScheduledAction(long tick, Action action, UUID player) {}

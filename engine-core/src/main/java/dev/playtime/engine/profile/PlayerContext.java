package dev.playtime.engine.profile;

import dev.playtime.engine.util.Id;

import java.util.Set;
import java.util.UUID;

/** Snapshot of the facts profile assignment can look at. */
public record PlayerContext(UUID uuid, String name, String team, String dimension,
                            double x, double y, double z, Set<Id> worldFlags) {}

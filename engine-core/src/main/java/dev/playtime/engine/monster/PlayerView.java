package dev.playtime.engine.monster;

import dev.playtime.engine.util.Vec3;

import java.util.UUID;

/** What a monster can know about one candidate player this tick. */
public record PlayerView(UUID id, Vec3 position, Vec3 eyePosition, Vec3 lookDirection, float health,
                         boolean sneaking, boolean sprinting, boolean isHost, boolean hidden) {}

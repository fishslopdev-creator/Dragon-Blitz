package dev.playtime.engine.monster;

import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;

import java.util.UUID;

/** A sound monsters may hear: footsteps, GrabPack fire, a scripted distraction. */
public record Noise(Vec3 position, double loudness, Id tag, UUID source) {}

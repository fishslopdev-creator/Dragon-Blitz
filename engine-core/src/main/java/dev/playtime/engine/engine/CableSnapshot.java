package dev.playtime.engine.engine;

import dev.playtime.engine.grabpack.CableState;
import dev.playtime.engine.hand.LauncherSide;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;

import java.util.UUID;

/** Cable data broadcast to clients for rendering (hand position, state, hand id). */
public record CableSnapshot(UUID player, LauncherSide side, Id hand, CableState state, Vec3 handPosition, boolean charged) {}

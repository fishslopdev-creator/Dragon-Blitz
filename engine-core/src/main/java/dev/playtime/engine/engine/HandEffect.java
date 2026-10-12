package dev.playtime.engine.engine;

import dev.playtime.engine.util.Vec3;

import java.util.UUID;

/**
 * Physical or visual results of a hand action that the game layer must perform:
 * the engine decides <em>whether</em> something happens, the game decides <em>how</em> it looks.
 */
public record HandEffect(Type type, UUID player, Vec3 position, Vec3 vector, String entityRef, int ticks, double strength) {
    public enum Type {
        /** Reel an entity/object towards the player. */
        PULL_ENTITY,
        /** Pull the player towards an anchor (bounded velocity in {@code vector}). */
        SELF_PULL,
        /** Launch the player (jump pad) with velocity {@code vector}. */
        LAUNCH_PLAYER,
        /** Place a temporary light (flare) for {@code ticks}. */
        LIGHT,
        /** Push an entity or break a block tagged as pressure-breakable at {@code position}. */
        PRESSURE_BLAST,
        /** Ignite a block or object tagged as flammable at {@code position} (flares). */
        BURN,
        /** Pulse ring visual with radius {@code strength}. */
        PULSE_RING,
        /** Electrical arc visual along the cable. */
        ARC,
        /** Hand charge indicator on the client for {@code ticks}. */
        CHARGED,
        /** Red-cross / buzzer feedback for an invalid target or excessive range. */
        INVALID
    }

    public static HandEffect at(Type type, UUID player, Vec3 pos) {
        return new HandEffect(type, player, pos, null, null, 0, 0);
    }
}

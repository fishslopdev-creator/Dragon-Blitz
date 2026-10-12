package dev.playtime.engine.access;

import dev.playtime.engine.hand.HandStats;
import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.util.Id;

import java.util.Set;

/**
 * What a player presents to a lock. {@code heldCredentials} is gathered by the game layer
 * from the player's inventory plus key items in progress; {@code presentedHand} is the hand
 * fired at a scanner (null for plain use).
 */
public record AccessRequest(Facts facts, Set<Id> heldCredentials, Set<Id> unlockedHands,
                            HandStats presentedHand, boolean devicePowered) {}

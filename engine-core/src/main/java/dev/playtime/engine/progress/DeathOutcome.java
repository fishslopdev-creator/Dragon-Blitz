package dev.playtime.engine.progress;

import dev.playtime.engine.util.Id;

import java.util.Set;

/** What the game layer should do after a player dies. */
public record DeathOutcome(boolean keepInventory, Set<Id> lostHands, Set<Id> lostKeys, Id respawnCheckpoint) {}

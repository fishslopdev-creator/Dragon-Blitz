package dev.playtime.engine.progress;

import java.util.UUID;

/** Confirmation required before a New Game overwrites an existing save. */
public record NewGameTicket(UUID token, UUID player, int slot, long expiresAtTick) {}

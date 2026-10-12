package dev.playtime.engine.progress;

import dev.playtime.engine.config.ProgressionMode;
import dev.playtime.engine.config.ResetAuthority;
import dev.playtime.engine.config.WorldConfig;
import dev.playtime.engine.util.Id;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves which save is authoritative for a player and owns the New Game / Continue /
 * death rules. All methods are meant to run on the server thread.
 */
public final class ProgressionService {
    public static final long NEW_GAME_CONFIRM_TICKS = 20 * 60;

    private final PlayerSaveStore store;
    private WorldConfig config;
    private SaveSlot sharedSlot;
    private Runnable sharedDirtyListener = () -> {};
    private final Map<UUID, NewGameTicket> pendingNewGames = new HashMap<>();

    public ProgressionService(WorldConfig config, PlayerSaveStore store, SaveSlot sharedSlot) {
        this.config = config;
        this.store = store;
        this.sharedSlot = sharedSlot == null ? new SaveSlot() : sharedSlot;
    }

    public void setConfig(WorldConfig config) {
        this.config = config;
    }

    public void onSharedDirty(Runnable listener) {
        this.sharedDirtyListener = listener;
    }

    public SaveSlot sharedSlot() {
        return sharedSlot;
    }

    public PlayerSaveStore store() {
        return store;
    }

    public ProgressionMode mode() {
        return config.multiplayer.progressionMode;
    }

    public boolean isShared() {
        return mode() != ProgressionMode.INDIVIDUAL;
    }

    /** The slot whose progress applies to {@code player}. */
    public SaveSlot slotFor(UUID player) {
        if (isShared()) return sharedSlot;
        return store.get(player).activeSlot(config.saveSlots);
    }

    /** The progress that applies to {@code player}, or empty if they have not started a game. */
    public Optional<PlayerProgress> progress(UUID player) {
        return Optional.ofNullable(slotFor(player).progress);
    }

    /** Progress for gameplay; starts an empty save if none exists so events are never lost. */
    public PlayerProgress progressOrCreate(UUID player) {
        SaveSlot slot = slotFor(player);
        if (slot.progress == null) {
            slot.progress = new PlayerProgress();
            slot.progress.chapter = config.startingChapter;
            slot.createdAt = System.currentTimeMillis();
        }
        return slot.progress;
    }

    public void markDirty(UUID player) {
        if (isShared()) {
            sharedSlot.lastPlayedAt = System.currentTimeMillis();
            sharedDirtyListener.run();
        } else {
            store.markDirty(player);
        }
    }

    public UUID hostOf(UUID fallback) {
        UUID host = config.multiplayer.hostPlayer;
        return host != null ? host : fallback;
    }

    /** Whether {@code player} may reset or restart shared state (new game, puzzle reset, chase restart). */
    public boolean mayReset(UUID player, boolean isOperator) {
        if (isOperator) return true;
        if (mode() == ProgressionMode.INDIVIDUAL) return true;
        ResetAuthority authority = config.multiplayer.resetAuthority;
        return switch (authority) {
            case OPERATORS_ONLY -> false;
            case HOST_AND_OPERATORS -> player.equals(config.multiplayer.hostPlayer);
            case ANY_PARTICIPANT -> true;
        };
    }

    // ---------------------------------------------------------------- New Game / Continue

    public sealed interface NewGameResult permits Started, NeedsConfirmation, Denied {}

    public record Started(PlayerProgress progress) implements NewGameResult {}

    public record NeedsConfirmation(NewGameTicket ticket) implements NewGameResult {}

    public record Denied(String reason) implements NewGameResult {}

    /**
     * First step of New Game. Starts immediately on an empty slot; otherwise returns a
     * ticket that must be confirmed so an existing save is never destroyed by one click.
     */
    public NewGameResult requestNewGame(UUID player, int slotIndex, boolean isOperator, long tick) {
        if (!mayReset(player, isOperator)) {
            return new Denied("Only the host or an operator can start a new game on this world.");
        }
        SaveSlot slot = targetSlot(player, slotIndex);
        if (slot.progress != null && slot.progress.isStarted()) {
            NewGameTicket ticket = new NewGameTicket(UUID.randomUUID(), player, slotIndex, tick + NEW_GAME_CONFIRM_TICKS);
            pendingNewGames.put(player, ticket);
            return new NeedsConfirmation(ticket);
        }
        return new Started(startFresh(player, slotIndex));
    }

    public NewGameResult confirmNewGame(UUID player, UUID token, boolean isOperator, long tick) {
        NewGameTicket ticket = pendingNewGames.get(player);
        if (ticket == null || !ticket.token().equals(token)) {
            return new Denied("That confirmation is no longer valid. Choose New Game again.");
        }
        pendingNewGames.remove(player);
        if (tick > ticket.expiresAtTick()) {
            return new Denied("The confirmation expired. Choose New Game again.");
        }
        if (!mayReset(player, isOperator)) {
            return new Denied("Only the host or an operator can start a new game on this world.");
        }
        return new Started(startFresh(player, ticket.slot()));
    }

    public void cancelNewGame(UUID player) {
        pendingNewGames.remove(player);
    }

    /** Continue: selects the slot to play and reports whether it has progress to resume. */
    public Optional<PlayerProgress> continueGame(UUID player, int slotIndex) {
        if (!isShared()) {
            PlayerSave save = store.get(player);
            save.activeSlot = Math.max(0, Math.min(config.saveSlots - 1, slotIndex));
            store.markDirty(player);
        }
        return progress(player);
    }

    private SaveSlot targetSlot(UUID player, int slotIndex) {
        if (isShared()) return sharedSlot;
        PlayerSave save = store.get(player);
        return save.slot(slotIndex, config.saveSlots);
    }

    private PlayerProgress startFresh(UUID player, int slotIndex) {
        SaveSlot slot = targetSlot(player, slotIndex);
        PlayerProgress fresh = new PlayerProgress();
        fresh.chapter = config.startingChapter;
        slot.progress = fresh;
        slot.createdAt = System.currentTimeMillis();
        slot.lastPlayedAt = slot.createdAt;
        if (!isShared()) {
            PlayerSave save = store.get(player);
            save.activeSlot = Math.max(0, Math.min(config.saveSlots - 1, slotIndex));
        }
        markDirty(player);
        return fresh;
    }

    // ---------------------------------------------------------------- Death

    /**
     * Applies the world's death rules. {@code keepItemsOverride} / {@code keepHandsOverride}
     * come from the player's setup profile and win over the world rule when non-null.
     */
    public DeathOutcome onDeath(UUID player, Boolean keepItemsOverride, Boolean keepHandsOverride) {
        PlayerProgress p = progressOrCreate(player);
        p.deaths++;
        boolean keepItems = keepItemsOverride != null ? keepItemsOverride : config.rules.keepItemsOnDeath;
        boolean keepHands = keepHandsOverride != null ? keepHandsOverride : config.rules.keepHandsOnDeath;
        Set<Id> lostHands = new LinkedHashSet<>();
        Set<Id> lostKeys = new LinkedHashSet<>();
        // In shared saves one player's death must not strip unlocks the whole team earned.
        if (!isShared()) {
            if (!keepHands) {
                lostHands.addAll(p.handsSinceCheckpoint);
                p.unlockedHands.removeAll(lostHands);
                p.handsSinceCheckpoint.clear();
                if (lostHands.contains(p.selectedLeftHand)) p.selectedLeftHand = null;
                if (lostHands.contains(p.selectedRightHand)) p.selectedRightHand = null;
            }
            if (!config.rules.keepUncheckpointedKeysOnDeath) {
                lostKeys.addAll(p.keysSinceCheckpoint);
                p.keys.removeAll(lostKeys);
                p.keysSinceCheckpoint.clear();
            }
        }
        markDirty(player);
        return new DeathOutcome(keepItems, lostHands, lostKeys, p.checkpoint);
    }
}

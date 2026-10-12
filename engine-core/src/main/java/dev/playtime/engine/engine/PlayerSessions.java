package dev.playtime.engine.engine;

import dev.playtime.engine.config.MenuButtonType;
import dev.playtime.engine.config.MenuTrigger;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.hand.HandDefinition;
import dev.playtime.engine.hand.HandPickupConfig;
import dev.playtime.engine.hand.HandService;
import dev.playtime.engine.logic.TriggerType;
import dev.playtime.engine.menu.MenuDecision;
import dev.playtime.engine.menu.PlayMode;
import dev.playtime.engine.profile.PlayerContext;
import dev.playtime.engine.profile.PlayerProfile;
import dev.playtime.engine.profile.ProfileApplier;
import dev.playtime.engine.profile.StartingKit;
import dev.playtime.engine.progress.DeathOutcome;
import dev.playtime.engine.progress.ObjectiveStatus;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.progress.PlayerSave;
import dev.playtime.engine.progress.ProgressionService;
import dev.playtime.engine.progress.SaveSlot;
import dev.playtime.engine.story.ChapterDefinition;
import dev.playtime.engine.story.CheckpointDefinition;
import dev.playtime.engine.story.CollectibleDefinition;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Player lifecycle: joining, the main menu (Continue / New Game), deaths, checkpoints,
 * chapters, collectibles and hand unlocks.
 */
public final class PlayerSessions {
    private final PlaytimeEngine e;

    PlayerSessions(PlaytimeEngine engine) {
        this.e = engine;
    }

    // ------------------------------------------------------------------ profiles

    public PlayerProfile profileOf(UUID player) {
        if (player == null || player.equals(PlaytimeEngine.WORLD)) return null;
        PlayerPosition pos = e.bridge.position(player);
        PlayerContext ctx = new PlayerContext(player, e.bridge.playerName(player), e.bridge.team(player),
                pos == null ? null : pos.dimension(), pos == null ? 0 : pos.x(), pos == null ? 0 : pos.y(),
                pos == null ? 0 : pos.z(), e.state.worldFlags);
        Id explicit = e.saves.get(player).assignedProfile;
        return e.profiles.resolve(ctx, explicit, e.config.defaultProfile).orElse(null);
    }

    public void assignProfile(UUID player, Id profile) {
        e.saves.get(player).assignedProfile = profile;
        e.saves.markDirty(player);
    }

    // ------------------------------------------------------------------ connection events

    public void onJoin(UUID player, PlayMode mode) {
        PlayerSave save = e.saves.get(player);
        boolean first = !save.hasEnteredWorld;
        save.hasEnteredWorld = true;
        save.lastKnownName = e.bridge.playerName(player);
        e.saves.markDirty(player);
        if (!e.config.enabled) return;
        if (e.config.multiplayer.hostPlayer == null && e.progression.mode() == dev.playtime.engine.config.ProgressionMode.HOST_OWNED) {
            e.config.multiplayer.hostPlayer = player;
            e.saveConfig();
        }
        PlayerProfile profile = profileOf(player);
        MenuDecision decision = profile != null && Boolean.FALSE.equals(profile.showMainMenu)
                ? MenuDecision.no("profile disables the menu")
                : e.menus.onJoin(e.config, player, mode, first, e.tick);
        if (decision.show()) {
            openMenu(player, decision.cause());
        } else if (e.progression.progress(player).isEmpty() && (mode == PlayMode.SURVIVAL || mode == PlayMode.ADVENTURE)) {
            // No menu: start straight into a fresh game.
            var r = e.progression.requestNewGame(player, 0, true, e.tick);
            if (r instanceof ProgressionService.Started started) beginGame(player, started.progress(), true);
        }
        e.bridge.syncPlayer(player);
        e.trigger(TriggerType.PLAYER_JOIN, null, player);
    }

    public void onRespawn(UUID player, PlayMode mode, boolean afterDeath) {
        MenuDecision d = e.menus.onRespawn(e.config, player, mode, afterDeath, e.tick);
        if (d.show()) openMenu(player, d.cause());
        e.bridge.syncPlayer(player);
    }

    public void onDimensionChange(UUID player, PlayMode mode) {
        e.menus.onDimensionChange(player, mode);
        e.grabPack.cancelAll(player);
    }

    public void onGameModeChange(UUID player, PlayMode from, PlayMode to) {
        MenuDecision d = e.menus.onGameModeChange(e.config, player, from, to, e.tick);
        if (d.show()) openMenu(player, d.cause());
    }

    public void onLeave(UUID player) {
        e.menus.onDisconnect(player);
        e.chases.onPlayerLeft(player);
        e.grabPack.cancelAll(player);
        e.dialogues.end(player);
        e.progression.cancelNewGame(player);
        e.saves.unload(player);
    }

    /** Applies death rules. The game layer uses {@code keepInventory} and respawns at {@link #respawnPoint}. */
    public DeathOutcome onDeath(UUID player) {
        PlayerProfile profile = profileOf(player);
        DeathOutcome outcome = e.progression.onDeath(player,
                profile == null ? null : profile.keepItemsOnDeath, profile == null ? null : profile.keepHandsOnDeath);
        e.grabPack.cancelAll(player);
        e.dialogues.end(player);
        if (e.chases.chaseOf(player).isPresent()) e.chases.onPlayerCaught(player);
        e.puzzles.onPlayerDeath(player, e.config.rules.resetActivePuzzlesOnDeath);
        if (e.config.rules.resetPowerOnDeath) {
            for (Device d : e.graph.devices()) {
                if ((d.type == DeviceType.GENERATOR || d.type == DeviceType.POWER_SWITCH)
                        && !"false".equals(d.setting("reset_on_death", "true"))) {
                    e.signals.setActive(d.id, false, null);
                }
            }
        }
        for (Id hand : outcome.lostHands()) e.bridge.showStatus(player, "Lost: " + handName(hand));
        e.bridge.syncPlayer(player);
        return outcome;
    }

    /** Where the player should respawn: their checkpoint, or null for the world spawn. */
    public Optional<Respawn> respawnPoint(UUID player) {
        return e.progression.progress(player).map(p -> p.checkpoint).flatMap(this::checkpoint);
    }

    public record Respawn(BlockRef pos, float yaw) {}

    Optional<Respawn> checkpoint(Id id) {
        if (id == null) return Optional.empty();
        CheckpointDefinition def = e.content.<CheckpointDefinition>get(ContentKind.CHECKPOINTS, id).orElse(null);
        if (def != null && def.pos != null) return Optional.of(new Respawn(def.pos, def.yaw));
        Device d = e.graph.device(id).filter(x -> x.type == DeviceType.CHECKPOINT).orElse(null);
        if (d != null && d.pos != null) {
            float yaw;
            try {
                yaw = Float.parseFloat(d.setting("yaw", "0"));
            } catch (NumberFormatException ex) {
                yaw = 0;
            }
            return Optional.of(new Respawn(new BlockRef(d.pos.dimension(), d.pos.x(), d.pos.y() + 1, d.pos.z()), yaw));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ main menu

    public void openMenu(UUID player, MenuTrigger cause) {
        e.bridge.openMainMenu(player, menuPayload(player, cause));
    }

    public MenuPayload menuPayload(UUID player, MenuTrigger cause) {
        List<MenuPayload.SlotSummary> slots = new ArrayList<>();
        int active = 0;
        if (e.progression.isShared()) {
            slots.add(summary(0, e.progression.sharedSlot()));
        } else {
            PlayerSave save = e.saves.get(player);
            for (int i = 0; i < e.config.saveSlots; i++) slots.add(summary(i, save.slot(i, e.config.saveSlots)));
            active = save.activeSlot;
        }
        PlayerProgress current = e.progression.progress(player).orElse(null);
        List<MenuPayload.ChapterEntry> chapters = new ArrayList<>();
        List<ChapterDefinition> defs = new ArrayList<>(e.content.<ChapterDefinition>all(ContentKind.CHAPTERS));
        defs.sort(Comparator.comparingInt(c -> c.order));
        for (ChapterDefinition c : defs) {
            boolean reached = current != null && (c.id.equals(current.chapter)
                    || current.flags.contains(new Id(c.id.namespace(), "chapter_complete/" + c.id.path())));
            if (c.hiddenUntilReached && !reached) continue;
            chapters.add(new MenuPayload.ChapterEntry(c.id.toString(), c.name, c.subtitle, reached));
        }
        boolean mayNew = e.progression.mayReset(player, e.bridge.isOperator(player));
        return new MenuPayload(e.config.menuTitle(), e.config.menuSubtitle(), e.config.mainMenu, cause, slots, active,
                e.progression.isShared(), mayNew, chapters);
    }

    private MenuPayload.SlotSummary summary(int index, SaveSlot slot) {
        PlayerProgress p = slot.progress;
        if (p == null) return new MenuPayload.SlotSummary(index, true, null, null, 0, 0, false, 0);
        String chapter = p.chapter == null ? "" : e.content.<ChapterDefinition>get(ContentKind.CHAPTERS, p.chapter)
                .map(c -> c.name).orElse(p.chapter.path());
        String checkpoint = p.checkpoint == null ? "" : e.content.<CheckpointDefinition>get(ContentKind.CHECKPOINTS, p.checkpoint)
                .map(c -> c.name != null ? c.name : c.id.path()).orElse(p.checkpoint.path());
        return new MenuPayload.SlotSummary(index, false, chapter, checkpoint, p.unlockedHands.size(),
                p.collectibles.size(), p.gameCompleted, slot.lastPlayedAt);
    }

    /** Continue pressed. Only accepted while the server has this player's menu open. */
    public String menuContinue(UUID player, int slot) {
        if (!e.menus.isMenuOpen(player)) return "The menu is not open.";
        if (!buttonVisible(MenuButtonType.CONTINUE)) return "Continue is disabled in this world.";
        Optional<PlayerProgress> progress = e.progression.continueGame(player, slot);
        if (progress.isEmpty() || !progress.get().isStarted() && progress.get().chapter == null) {
            return "There is no save to continue. Start a new game.";
        }
        e.menus.onMenuClosed(player);
        respawnAtCheckpoint(player, null);
        e.bridge.syncPlayer(player);
        return null;
    }

    /** New Game pressed. Returns the progression result; NeedsConfirmation must be confirmed by the client. */
    public ProgressionService.NewGameResult menuNewGame(UUID player, int slot) {
        if (!e.menus.isMenuOpen(player)) return new ProgressionService.Denied("The menu is not open.");
        if (!buttonVisible(MenuButtonType.NEW_GAME)) return new ProgressionService.Denied("New Game is disabled in this world.");
        ProgressionService.NewGameResult r = e.progression.requestNewGame(player, slot, e.bridge.isOperator(player), e.tick);
        if (r instanceof ProgressionService.Started s) {
            e.menus.onMenuClosed(player);
            beginGame(player, s.progress(), true);
        }
        return r;
    }

    public ProgressionService.NewGameResult menuConfirmNewGame(UUID player, UUID token) {
        if (!e.menus.isMenuOpen(player)) return new ProgressionService.Denied("The menu is not open.");
        ProgressionService.NewGameResult r = e.progression.confirmNewGame(player, token, e.bridge.isOperator(player), e.tick);
        if (r instanceof ProgressionService.Started s) {
            e.menus.onMenuClosed(player);
            beginGame(player, s.progress(), true);
        }
        return r;
    }

    public void menuClosed(UUID player) {
        e.menus.onMenuClosed(player);
        e.progression.cancelNewGame(player);
    }

    private boolean buttonVisible(MenuButtonType type) {
        return e.config.mainMenu.orderedVisibleButtons().stream().anyMatch(b -> b.type == type);
    }

    /**
     * Starts a fresh save: resets shared world state in co-op modes, applies the setup
     * profile, hands out the starting kit and enters the starting chapter.
     */
    void beginGame(UUID player, PlayerProgress progress, boolean giveKit) {
        if (e.progression.isShared()) resetWorldRuntime();
        PlayerProfile profile = profileOf(player);
        if (profile != null) {
            StartingKit kit = ProfileApplier.apply(profile, progress);
            if (giveKit) e.bridge.applyStartingKit(player, kit);
        }
        e.progression.markDirty(player);
        Id chapter = progress.chapter != null ? progress.chapter : e.config.startingChapter;
        startChapter(player, chapter);
        respawnAtCheckpoint(player, null);
        e.saves.saveDirty();
        e.bridge.syncPlayer(player);
    }

    /** Puts doors, devices, puzzles and world flags back to their initial state (new co-op game, test reset). */
    public void resetWorldRuntime() {
        e.chases.active().stream().map(c -> c.definition.id).toList().forEach(e.chases::stop);
        e.director.clear();
        for (var def : e.puzzles.definitions()) e.puzzles.reset(def.id, true);
        e.resetDevices(e.graph.devices().stream().map(d -> d.id).toList());
        for (var door : e.doors.all()) {
            var s = e.doors.state(door.id);
            s.forcedLocked = false;
            if (door.startsOpen) e.doors.open(door.id, e.tick); else e.doors.close(door.id);
        }
        e.state.worldFlags.clear();
        e.state.firedEvents.clear();
        e.state.completedChases.clear();
        e.signals.restorePending(List.of());
        e.actions.clear();
    }

    // ------------------------------------------------------------------ chapters & checkpoints

    public void startChapter(UUID player, Id chapter) {
        if (chapter == null) return;
        PlayerProgress p = e.progression.progressOrCreate(player);
        p.chapter = chapter;
        e.progression.markDirty(player);
        ChapterDefinition def = e.content.<ChapterDefinition>get(ContentKind.CHAPTERS, chapter).orElse(null);
        if (def != null) {
            e.bridge.showTitle(player, def.name, def.subtitle, 80);
            if (def.startCheckpoint != null && p.checkpoint == null) setCheckpoint(player, def.startCheckpoint, false);
            e.actions.run(def.onStart, player);
        }
        e.trigger(TriggerType.CHAPTER_START, chapter, player);
    }

    public void setCheckpoint(UUID player, Id checkpoint, boolean announce) {
        if (checkpoint == null) return;
        PlayerProgress p = e.progression.progressOrCreate(player);
        if (checkpoint.equals(p.checkpoint)) return;
        p.reachCheckpoint(checkpoint);
        e.progression.markDirty(player);
        e.saves.saveDirty();
        if (announce) e.bridge.showStatus(player, "Checkpoint reached");
    }

    public void respawnAtCheckpoint(UUID player, Id checkpoint) {
        Id id = checkpoint != null ? checkpoint : e.progression.progress(player).map(p -> p.checkpoint).orElse(null);
        checkpoint(id).ifPresent(r -> e.bridge.teleport(player, r.pos(), r.yaw()));
    }

    public void returnToMenu(UUID player) {
        MenuDecision d = e.menus.requestOpen(e.config, player, e.tick);
        if (d.show()) openMenu(player, null);
    }

    // ------------------------------------------------------------------ unlocks

    public boolean grantHand(UUID player, Id hand, boolean equip) {
        if (e.hands.get(hand).isEmpty()) return false;
        if (!e.handService.grant(player, hand, equip)) return false;
        announceHand(player, hand);
        return true;
    }

    /** Hand pickup collected. Returns the service result so the game layer can hide the pickup. */
    public HandService.PickupResult pickupHand(UUID player, HandPickupConfig pickup) {
        HandService.PickupResult r = e.handService.collect(player, pickup);
        if (r.message() != null && !r.granted()) e.bridge.showStatus(player, r.message());
        if (r.granted()) {
            announceHand(player, r.hand().id);
            if (r.grantToAll()) {
                for (UUID other : e.bridge.onlinePlayers()) if (!other.equals(player)) grantHand(other, r.hand().id, pickup.autoEquip);
            }
            if (pickup.objective != null) {
                e.progression.progressOrCreate(player).objectives.put(pickup.objective, ObjectiveStatus.ACTIVE);
                e.progression.markDirty(player);
            }
            if (pickup.event != null) e.story.fire(pickup.event, player, false);
        }
        return r;
    }

    private void announceHand(UUID player, Id hand) {
        HandDefinition def = e.hands.get(hand).orElse(null);
        if (def == null) return;
        e.bridge.showUnlock(player, def.displayName, def.description, def.icon);
        Id sound = def.sounds.get("pickup");
        if (sound != null) e.bridge.playSound(player, sound, null, 1f);
        PlayerProfile profile = profileOf(player);
        boolean tutorials = e.config.rules.tutorialsEnabled && (profile == null || profile.tutorial);
        PlayerProgress p = e.progression.progressOrCreate(player);
        if (tutorials && def.tutorial != null && p.completedTutorials.add(def.tutorial)) {
            e.progression.markDirty(player);
            e.story.fire(def.tutorial, player, true);
        }
        e.trigger(TriggerType.HAND_UNLOCKED, hand, player);
        e.bridge.syncPlayer(player);
    }

    public boolean collect(UUID player, Id collectible, boolean fromPickup) {
        CollectibleDefinition def = e.content.<CollectibleDefinition>get(ContentKind.COLLECTIBLES, collectible).orElse(null);
        if (def == null) return false;
        PlayerProgress p = e.progression.progressOrCreate(player);
        boolean added = p.collectibles.add(collectible);
        if (!added && fromPickup) return false;
        e.progression.markDirty(player);
        e.bridge.showUnlock(player, def.title != null ? def.title : collectible.path(), "Added to your journal", null);
        if (def.audio != null) e.bridge.playRecording(player, def);
        if (added) {
            e.actions.run(def.onCollect, player);
            e.trigger(TriggerType.COLLECTIBLE, collectible, player);
        }
        e.bridge.syncPlayer(player);
        return added;
    }

    private String handName(Id hand) {
        return e.hands.get(hand).map(h -> h.displayName).orElse(hand.toString());
    }
}

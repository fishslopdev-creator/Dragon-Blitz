package dev.playtime.engine.engine;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.profile.ItemSpec;
import dev.playtime.engine.progress.ObjectiveStatus;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.story.ChapterDefinition;
import dev.playtime.engine.story.CheckpointDefinition;
import dev.playtime.engine.story.CollectibleDefinition;
import dev.playtime.engine.story.ObjectiveDefinition;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes {@link Action}s. Delayed actions are queued (and saved with the world).
 * Each action is isolated: one failing action is logged and the rest still run.
 */
final class ActionRunner {
    private static final Logger LOG = Logger.getLogger("PlaytimeEngine");
    private static final int MAX_DEPTH = 24;

    private final PlaytimeEngine e;
    private final PriorityQueue<ScheduledAction> queue = new PriorityQueue<>((a, b) -> Long.compare(a.tick(), b.tick()));
    private int depth;

    ActionRunner(PlaytimeEngine engine) {
        this.e = engine;
    }

    void run(List<Action> actions, UUID player) {
        if (actions == null || actions.isEmpty()) return;
        if (depth >= MAX_DEPTH) {
            LOG.warning("Action chain too deep; check events/connections that trigger each other");
            return;
        }
        depth++;
        try {
            for (Action a : actions) {
                if (a == null || a.type == null) continue;
                if (a.delay > 0) {
                    Action copy = dev.playtime.engine.util.Json.copy(a, Action.class);
                    copy.delay = 0;
                    queue.add(new ScheduledAction(e.tick + a.delay, copy, player));
                    continue;
                }
                try {
                    execute(a, player);
                } catch (RuntimeException ex) {
                    LOG.log(Level.SEVERE, "Action " + a.type + " (" + a.id + ") failed", ex);
                }
            }
        } finally {
            depth--;
        }
    }

    void tick() {
        while (!queue.isEmpty() && queue.peek().tick() <= e.tick) {
            ScheduledAction next = queue.poll();
            run(List.of(next.action()), next.player());
        }
    }

    List<ScheduledAction> pendingSnapshot() {
        return new ArrayList<>(queue);
    }

    void restore(List<ScheduledAction> pending) {
        queue.clear();
        if (pending != null) for (ScheduledAction a : pending) if (a != null && a.action() != null) queue.add(a);
    }

    void clear() {
        queue.clear();
    }

    private Collection<UUID> audience(UUID player) {
        return player != null ? List.of(player) : e.bridge.onlinePlayers();
    }

    private void execute(Action a, UUID player) {
        GameBridge b = e.bridge;
        switch (a.type) {
            case TITLE_CARD -> audience(player).forEach(p -> b.showTitle(p, a.text, a.subtitle, a.ticks > 0 ? a.ticks : 70));
            case SUBTITLE -> audience(player).forEach(p -> b.showStatus(p, a.text));
            case DIALOGUE -> {
                if (player != null && a.id != null) e.startDialogue(player, a.id);
            }
            case PLAY_SOUND -> {
                Id sound = a.sound != null ? a.sound : a.id;
                if (sound != null) {
                    if (a.pos != null) b.playSound(null, sound, a.pos, a.volume);
                    else audience(player).forEach(p -> b.playSound(p, sound, null, a.volume));
                }
            }
            case STOP_SOUND -> audience(player).forEach(p -> b.stopSound(p, a.sound != null ? a.sound : a.id));
            case PLAY_RECORDING -> e.content.<CollectibleDefinition>get(ContentKind.COLLECTIBLES, a.id)
                    .ifPresent(c -> audience(player).forEach(p -> b.playRecording(p, c)));
            case PARTICLES -> {
                if (a.pos != null && a.id != null) {
                    b.particles(a.id, a.pos, Math.min(Math.max(1, a.value), e.config.atmosphere.particleBudgetPerTick));
                }
            }
            case CAMERA_SHAKE -> audience(player).forEach(p -> b.cameraShake(p, a.ticks > 0 ? a.ticks : 20,
                    a.value > 0 ? a.value / 10f : 0.5f));
            case SCREEN_FADE -> audience(player).forEach(p -> b.screenFade(p, a.ticks > 0 ? a.ticks : 20));
            case CREDITS -> audience(player).forEach(p -> b.showCredits(p, a.id));

            case SET_FLAG -> forSaves(player, p -> p.flags.add(a.id));
            case CLEAR_FLAG -> forSaves(player, p -> p.flags.remove(a.id));
            case SET_WORLD_FLAG -> e.state.worldFlags.add(a.id);
            case CLEAR_WORLD_FLAG -> e.state.worldFlags.remove(a.id);
            case OBJECTIVE_SET -> setObjective(player, a.id, ObjectiveStatus.ACTIVE);
            case OBJECTIVE_COMPLETE -> setObjective(player, a.id, ObjectiveStatus.COMPLETED);
            case OBJECTIVE_FAIL -> setObjective(player, a.id, ObjectiveStatus.FAILED);
            case JOURNAL_ADD -> {
                if (player != null) e.collect(player, a.id, false);
            }
            case GRANT_HAND -> {
                if (a.id != null) for (UUID p : audience(player)) e.grantHand(p, a.id, true);
            }
            case GIVE_KEY -> forSaves(player, p -> p.addKey(a.id));
            case TAKE_KEY -> forSaves(player, p -> {
                p.keys.remove(a.id);
                p.keysSinceCheckpoint.remove(a.id);
            });
            case GIVE_ITEM -> {
                if (a.id != null) {
                    ItemSpec spec = new ItemSpec(a.id, Math.max(1, a.value));
                    spec.components = a.param("components", null);
                    audience(player).forEach(p -> b.giveItem(p, spec));
                }
            }
            case SET_CHECKPOINT -> {
                for (UUID p : audience(player)) e.setCheckpoint(p, a.id, true);
            }
            case SET_CHAPTER -> {
                for (UUID p : audience(player)) e.startChapter(p, a.id);
            }
            case CHAPTER_COMPLETE -> {
                Id chapter = a.id;
                for (UUID p : audience(player)) {
                    PlayerProgress prog = e.progression.progressOrCreate(p);
                    Id done = chapter != null ? chapter : prog.chapter;
                    ChapterDefinition def = e.content.<ChapterDefinition>get(ContentKind.CHAPTERS, done).orElse(null);
                    prog.flags.add(new Id(done.namespace(), "chapter_complete/" + done.path()));
                    e.progression.markDirty(p);
                    if (def != null) {
                        run(def.onComplete, p);
                        if (def.next != null) e.startChapter(p, def.next);
                    }
                }
            }
            case GAME_COMPLETE -> forSaves(player, p -> {
                p.gameCompleted = true;
                p.endingReached = a.id;
            });

            case DOOR_OPEN -> e.doors.open(a.id, e.tick);
            case DOOR_CLOSE -> e.doors.close(a.id);
            case DOOR_LOCK -> e.doors.setForcedLock(a.id, true);
            case DOOR_UNLOCK -> e.doors.setForcedLock(a.id, false);
            case LIGHTS -> {
                if (a.area != null) b.setLights(a.area, a.on);
                if (a.id != null) e.signals.setActive(a.id, a.on, player);
            }
            case EMIT_SIGNAL -> {
                if (a.id != null) {
                    if (a.on) e.signals.pulse(a.id, player); else e.signals.setActive(a.id, false, player);
                }
            }
            case SET_DEVICE -> {
                if (a.id != null) e.signals.setActive(a.id, a.on, player);
            }
            case RESET_PUZZLE -> e.puzzles.reset(a.id, true);
            case SPAWN_MONSTER -> e.director.spawnFromAction(a, player);
            case DESPAWN_MONSTER -> e.director.despawnByDefinition(a.id);
            case START_CHASE -> e.chases.start(a.id, player, false);
            case STOP_CHASE -> e.chases.stop(a.id);
            case TRIGGER_EVENT -> e.story.fire(a.id, player, false);
            case TELEPORT_TO_CHECKPOINT -> {
                for (UUID p : audience(player)) e.respawnAtCheckpoint(p, a.id);
            }
            case RETURN_TO_MENU -> {
                for (UUID p : audience(player)) e.returnToMenu(p);
            }
        }
    }

    /**
     * Applies a progress change to the right save(s): the shared save in co-op modes,
     * otherwise the acting player's save, or every online player's for world-level actions.
     */
    private void forSaves(UUID player, java.util.function.Consumer<PlayerProgress> change) {
        List<UUID> targets = e.progression.isShared() ? List.of(player != null ? player : PlaytimeEngine.WORLD)
                : player != null ? List.of(player) : List.copyOf(e.bridge.onlinePlayers());
        for (UUID p : targets) {
            change.accept(e.progression.progressOrCreate(p));
            e.progression.markDirty(p);
        }
        for (UUID p : audience(player)) e.bridge.syncPlayer(p);
    }

    private void setObjective(UUID player, Id id, ObjectiveStatus status) {
        if (id == null) return;
        forSaves(player, p -> p.objectives.put(id, status));
        ObjectiveDefinition def = e.content.<ObjectiveDefinition>get(ContentKind.OBJECTIVES, id).orElse(null);
        if (def != null && !def.hidden) {
            String prefix = status == ObjectiveStatus.COMPLETED ? "Completed: " : status == ObjectiveStatus.FAILED ? "Failed: " : "";
            audience(player).forEach(p -> e.bridge.showObjective(p, prefix + def.text, 0));
        }
    }

    /** Teleport target for a checkpoint id (content or placed block). */
    static CheckpointDefinition checkpoint(PlaytimeEngine e, Id id) {
        return id == null ? null : e.content.<CheckpointDefinition>get(ContentKind.CHECKPOINTS, id).orElse(null);
    }
}

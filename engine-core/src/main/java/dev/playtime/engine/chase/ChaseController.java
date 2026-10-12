package dev.playtime.engine.chase;

import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.logic.TriggerType;
import dev.playtime.engine.util.Id;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs chase sequences: spawns the monster after the start delay, walks through stages
 * (with branches, timers and checkpoints) and applies the configured failure outcomes.
 * Only active chases are ticked.
 */
public final class ChaseController {
    private static final Logger LOG = Logger.getLogger("PlaytimeEngine");

    private final ChaseHost host;
    private final Map<Id, ChaseDefinition> definitions = new LinkedHashMap<>();
    private final Map<Id, ChaseInstance> active = new LinkedHashMap<>();
    /** World-level completion for non-repeatable chases (individual saves also record it). */
    private final Set<Id> completedOnce;

    public ChaseController(ChaseHost host, Set<Id> savedCompleted) {
        this.host = host;
        this.completedOnce = savedCompleted;
    }

    public void register(ChaseDefinition def) {
        definitions.put(def.id, def.normalize());
    }

    public Optional<ChaseDefinition> definition(Id id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Collection<ChaseDefinition> definitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Collection<ChaseInstance> active() {
        return Collections.unmodifiableCollection(active.values());
    }

    public boolean isActive(Id chase) {
        return active.containsKey(chase);
    }

    /** Returns the chase a player is currently in, if any. */
    public Optional<ChaseInstance> chaseOf(UUID player) {
        for (ChaseInstance c : active.values()) if (c.participants.contains(player)) return Optional.of(c);
        return Optional.empty();
    }

    /** Starts every chase whose trigger matches this game event. */
    public void onTrigger(TriggerType type, Id id, UUID player) {
        for (ChaseDefinition def : definitions.values()) {
            if (def.trigger.type != TriggerType.MANUAL && def.trigger.matches(type, id)) start(def.id, player, false);
        }
    }

    public String start(Id chaseId, UUID player, boolean force) {
        ChaseDefinition def = definitions.get(chaseId);
        if (def == null) return "Unknown chase " + chaseId;
        if (active.containsKey(chaseId)) return "Chase already running";
        if (!force && !def.repeatable && completedOnce.contains(chaseId)) return "Chase already completed";
        if (!force && player != null && host.facts(player).chaseCompleted(chaseId) && !def.repeatable) {
            return "Chase already completed";
        }
        ChaseInstance inst = new ChaseInstance(def, player, host.tick());
        if (player != null) inst.participants.add(player);
        inst.participants.addAll(host.nearbyPlayers(player));
        active.put(chaseId, inst);
        host.run(def.onStart, player);
        enterStage(inst, 0);
        return null;
    }

    public boolean stop(Id chaseId) {
        ChaseInstance inst = active.remove(chaseId);
        if (inst == null) return false;
        cleanup(inst);
        return true;
    }

    public void tick() {
        if (active.isEmpty()) return;
        long now = host.tick();
        for (ChaseInstance inst : List.copyOf(active.values())) {
            try {
                tickInstance(inst, now);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Chase " + inst.definition.id + " failed; stopping it", e);
                stop(inst.definition.id);
            }
        }
    }

    private void tickInstance(ChaseInstance inst, long now) {
        ChaseDefinition def = inst.definition;
        if (!inst.monsterSpawned && now >= inst.spawnAtTick && def.monster != null) {
            inst.monster = host.spawnChaseMonster(def, inst.currentStage(), inst.triggeredBy);
            inst.monsterSpawned = true;
            if (inst.monster == null) LOG.warning("Chase " + def.id + " could not spawn monster " + def.monster);
        }
        if (inst.monsterSpawned && inst.monster != null && !host.isMonsterAlive(inst.monster)) {
            // Monster removed by recovery/despawn: respawn rather than freezing the chase.
            inst.monster = host.spawnChaseMonster(def, inst.currentStage(), inst.triggeredBy);
        }
        if (def.maxDurationTicks > 0 && now - inst.startedTick > def.maxDurationTicks) {
            failAll(inst, "The chase timed out.");
            return;
        }
        ChaseStage stage = inst.currentStage();
        if (stage == null) {
            complete(inst);
            return;
        }
        Facts facts = stageFacts(inst);
        for (ChaseBranch b : stage.branches) {
            if (b != null && b.next != null && !b.conditions.isEmpty() && Condition.allMatch(b.conditions, facts)) {
                host.run(b.actions, inst.triggeredBy);
                go(inst, b.next);
                return;
            }
        }
        if (Condition.allMatch(stage.success, facts)) {
            host.run(stage.onSuccess, inst.triggeredBy);
            go(inst, stage.next);
            return;
        }
        if (stage.timeLimitTicks > 0) {
            long left = inst.stageStartedTick + stage.timeLimitTicks - now;
            if (left < 0) {
                failAll(inst, "Out of time.");
            } else if (left % 20 == 0) {
                host.notify(inst.participants, stage.objective, (int) left);
            }
        }
    }

    /** Facts for stage checks: any participant satisfying a condition counts (co-op friendly). */
    private Facts stageFacts(ChaseInstance inst) {
        UUID primary = inst.triggeredBy;
        if (inst.participants.size() <= 1) return host.facts(primary);
        return new AnyOfFacts(inst.participants.stream().map(host::facts).toList());
    }

    private void go(ChaseInstance inst, String next) {
        if ("complete".equals(next)) {
            complete(inst);
            return;
        }
        if ("fail".equals(next)) {
            failAll(inst, "You failed.");
            return;
        }
        int target = next == null ? inst.stage + 1 : inst.definition.stageIndex(next);
        if (target < 0) {
            LOG.warning("Chase " + inst.definition.id + " references unknown stage '" + next + "'; completing");
            complete(inst);
            return;
        }
        if (target >= inst.definition.stages.size()) {
            complete(inst);
            return;
        }
        enterStage(inst, target);
    }

    private void enterStage(ChaseInstance inst, int index) {
        inst.stage = index;
        inst.stageStartedTick = host.tick();
        ChaseStage stage = inst.currentStage();
        if (stage == null) return;
        host.run(stage.onEnter, inst.triggeredBy);
        host.notify(inst.participants, stage.objective, stage.timeLimitTicks);
        if (inst.monster != null) host.updateChaseMonster(inst.monster, inst.definition, stage);
    }

    /** Called when a monster catches a player. */
    public void onPlayerCaught(UUID player) {
        chaseOf(player).ifPresent(inst -> {
            if (host.failsForAll() || inst.participants.size() <= 1) {
                failAll(inst, "You were caught.");
            } else {
                inst.participants.remove(player);
                applyFailure(inst, Set.of(player));
            }
        });
    }

    /** A player left or died outside the chase rules. */
    public void onPlayerLeft(UUID player) {
        chaseOf(player).ifPresent(inst -> {
            inst.participants.remove(player);
            if (inst.participants.isEmpty()) stop(inst.definition.id);
        });
    }

    private void failAll(ChaseInstance inst, String reason) {
        active.remove(inst.definition.id);
        host.notify(inst.participants, reason, 0);
        applyFailure(inst, new LinkedHashSet<>(inst.participants));
        cleanup(inst);
    }

    private void applyFailure(ChaseInstance inst, Set<UUID> players) {
        ChaseDefinition def = inst.definition;
        for (UUID p : players) host.run(def.onFail, p);
        if (def.failure.contains(FailureOutcome.RESET_PUZZLES)) host.resetPuzzles(def.resetPuzzles);
        ChaseStage stage = inst.currentStage();
        Id checkpoint = stage != null && stage.checkpoint != null ? stage.checkpoint : def.checkpoint;
        for (UUID p : players) {
            if (def.failure.contains(FailureOutcome.RETURN_TO_MENU)) host.returnToMenu(p);
            else if (def.failure.contains(FailureOutcome.RESPAWN_AT_CHECKPOINT)) host.respawnAtCheckpoint(p, checkpoint);
        }
        // Without RESTART_CHASE the chase is spent; it will not trigger again this session.
        if (!def.failure.contains(FailureOutcome.RESTART_CHASE)) completedOnce.add(def.id);
    }

    private void complete(ChaseInstance inst) {
        active.remove(inst.definition.id);
        if (!inst.definition.repeatable) completedOnce.add(inst.definition.id);
        host.run(inst.definition.onComplete, inst.triggeredBy);
        host.markCompleted(inst.definition, inst.participants);
        cleanup(inst);
    }

    private void cleanup(ChaseInstance inst) {
        if (inst.monster == null) return;
        if (inst.definition.despawnOnEnd) host.despawnMonster(inst.monster); else host.releaseMonster(inst.monster);
    }

    /** Clears world-level completion (admin reset of a test save). */
    public void forgetCompletion(Id chase) {
        completedOnce.remove(chase);
    }
}

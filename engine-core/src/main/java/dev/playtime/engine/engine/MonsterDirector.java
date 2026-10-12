package dev.playtime.engine.engine;

import dev.playtime.engine.chase.ChaseDefinition;
import dev.playtime.engine.chase.ChaseStage;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.monster.MonsterBrain;
import dev.playtime.engine.monster.MonsterDefinition;
import dev.playtime.engine.monster.MonsterIntent;
import dev.playtime.engine.monster.MonsterSenses;
import dev.playtime.engine.monster.MonsterState;
import dev.playtime.engine.monster.Noise;
import dev.playtime.engine.monster.RouteDefinition;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tracks engine-managed monsters: spawning (respecting per-world toggles and instance
 * limits), AI decisions, encounters, catches, stuns and despawn rules. Monster entities
 * are not saved by the game; encounters are re-created from their triggers after a restart.
 */
public final class MonsterDirector {
    private static final Logger LOG = Logger.getLogger("PlaytimeEngine");

    private final PlaytimeEngine e;
    private final Map<UUID, Instance> instances = new LinkedHashMap<>();

    public static final class Instance {
        public final UUID entity;
        public final MonsterDefinition definition;
        public final MonsterBrain brain;
        public final Id chase;
        Vec3 lastPosition;

        Instance(UUID entity, MonsterDefinition definition, MonsterBrain brain, Id chase, Vec3 spawn) {
            this.entity = entity;
            this.definition = definition;
            this.brain = brain;
            this.chase = chase;
            this.lastPosition = spawn;
        }
    }

    MonsterDirector(PlaytimeEngine engine) {
        this.e = engine;
    }

    public Collection<Instance> instances() {
        return Collections.unmodifiableCollection(instances.values());
    }

    public Instance instance(UUID entity) {
        return instances.get(entity);
    }

    /** Spawns a monster. Returns the entity id or null (disabled, limit reached, conditions unmet). */
    public UUID spawn(Id monsterId, BlockRef pos, Id chase, Id route, boolean force) {
        MonsterDefinition def = e.monsters.get(monsterId).orElse(null);
        if (def == null) {
            LOG.warning("Cannot spawn unknown monster " + monsterId);
            return null;
        }
        if (pos == null) {
            LOG.warning("Cannot spawn " + monsterId + ": no position");
            return null;
        }
        if (!e.config.isMonsterEnabled(monsterId)) return null;
        if (!force) {
            long count = instances.values().stream().filter(i -> i.definition.id.equals(monsterId)).count();
            if (count >= def.maxInstances) return null;
            if (!Condition.allMatch(def.spawnConditions, e.facts(null))) return null;
        }
        UUID entity = e.bridge.spawnMonster(def, pos);
        if (entity == null) return null;
        MonsterBrain brain = new MonsterBrain(def, e.config.rules.monsterDifficulty, e.config.multiplayer.targetPolicy,
                pos.center(), entity.getLeastSignificantBits());
        Id routeId = route != null ? route : def.patrolRoute;
        e.monsters.route(routeId).ifPresent(r -> brain.setRoute(points(r), r.loop));
        instances.put(entity, new Instance(entity, def, brain, chase, pos.center()));
        return entity;
    }

    UUID spawnForChase(ChaseDefinition chase, ChaseStage stage, UUID target) {
        UUID entity = spawn(chase.monster, chase.spawn, chase.id, null, true);
        if (entity == null) return null;
        Instance inst = instances.get(entity);
        inst.brain.setRecovery(chase.recovery);
        applyStage(inst, chase, stage, target);
        return entity;
    }

    void updateForStage(UUID entity, ChaseDefinition chase, ChaseStage stage) {
        Instance inst = instances.get(entity);
        if (inst != null) applyStage(inst, chase, stage, inst.brain.target());
    }

    private void applyStage(Instance inst, ChaseDefinition chase, ChaseStage stage, UUID target) {
        Id routeId = stage != null && stage.monsterRoute != null ? stage.monsterRoute : chase.route;
        List<Vec3> route = e.monsters.route(routeId).map(MonsterDirector::points).orElse(List.of());
        Double speed = stage != null && stage.monsterSpeed != null ? stage.monsterSpeed : chase.pursuitSpeed;
        inst.brain.beginChase(target, route, speed, e.tick);
    }

    /** Handles the SPAWN_MONSTER action: {@code id} is a monster id or a Monster Spawn Marker device. */
    void spawnFromAction(Action a, UUID player) {
        if (a.id == null) return;
        Device marker = e.graph.device(a.id).filter(d -> d.type == DeviceType.MONSTER_SPAWN).orElse(null);
        if (marker != null) {
            Id monster = marker.idSetting("monster");
            if (monster != null) spawn(monster, marker.pos, null, marker.idSetting("route"), a.param("force", "false").equals("true"));
            return;
        }
        Id route = Id.tryParse(a.param("route", null));
        spawn(a.id, a.pos, null, route, "true".equals(a.param("force", "false")));
    }

    void despawnByDefinition(Id monster) {
        for (Instance i : List.copyOf(instances.values())) {
            if (monster == null || i.definition.id.equals(monster)) despawn(i.entity);
        }
    }

    public void despawn(UUID entity) {
        if (instances.remove(entity) != null) e.bridge.despawnMonster(entity);
    }

    boolean isAlive(UUID entity) {
        return instances.containsKey(entity) && e.bridge.isMonsterAlive(entity);
    }

    /**
     * Called by the monster entity each tick. Returns null for entities the engine does not
     * manage (the entity should then remove itself, e.g. a leftover from before a restart).
     */
    public MonsterIntent think(UUID entity, MonsterSenses senses) {
        Instance inst = instances.get(entity);
        if (inst == null) return null;
        inst.lastPosition = senses.position();
        MonsterState before = inst.brain.state();
        MonsterIntent intent = inst.brain.think(senses, e.tick);
        if (intent.despawn()) {
            instances.remove(entity);
            return intent;
        }
        if (inst.brain.target() != null && (intent.state() == MonsterState.CHASE || intent.state() == MonsterState.INTRO)
                && before != intent.state()) {
            encounter(inst.brain.target(), inst.definition);
        }
        return intent;
    }

    private void encounter(UUID player, MonsterDefinition def) {
        PlayerProgress p = e.progression.progressOrCreate(player);
        if (p.encounteredMonsters.add(def.id)) {
            e.progression.markDirty(player);
            if (def.introEvent != null) e.story.fire(def.introEvent, player, false);
        }
    }

    /** The entity's attack connected with {@code player}. */
    public void onAttack(UUID entity, UUID player) {
        Instance inst = instances.get(entity);
        if (inst == null || !inst.definition.hostile) return;
        MonsterDefinition def = inst.definition;
        e.actions.run(def.onCatch, player);
        switch (def.captureOutcome) {
            case DAMAGE -> e.bridge.damagePlayer(player, entity, def.attackDamage, false);
            case KILL -> e.bridge.damagePlayer(player, entity, def.attackDamage, true);
            case CATCH -> e.onPlayerCaught(player);
        }
    }

    public void hear(Noise noise) {
        for (Instance i : instances.values()) {
            if (i.lastPosition != null && i.lastPosition.distanceTo(noise.position()) <= i.definition.hearingRange * 3) {
                i.brain.hear(noise);
            }
        }
    }

    /** Stuns vulnerable monsters within {@code radius} (flares). Returns how many were stunned. */
    public int stunNear(Vec3 pos, double radius, Id ability) {
        int n = 0;
        for (Instance i : instances.values()) {
            if (i.lastPosition != null && i.lastPosition.distanceTo(pos) <= radius && i.brain.stun(ability, e.tick)) n++;
        }
        return n;
    }

    /** Despawns monsters whose despawn flag is now set. Cheap: only live instances are checked. */
    void checkDespawnFlags() {
        for (Instance i : List.copyOf(instances.values())) {
            Id flag = i.definition.despawnFlag;
            if (flag == null) continue;
            boolean set = e.state.worldFlags.contains(flag);
            if (!set) {
                for (UUID p : e.bridge.onlinePlayers()) {
                    if (e.facts(p).flag(flag)) {
                        set = true;
                        break;
                    }
                }
            }
            if (set) despawn(i.entity);
        }
    }

    /** Collects stuck warnings for the validator/debug command. */
    public List<String> warnings() {
        List<String> out = new ArrayList<>();
        for (Instance i : instances.values()) {
            for (String w : i.brain.warnings()) out.add(i.definition.id + ": " + w);
        }
        return out;
    }

    void clear() {
        for (UUID id : List.copyOf(instances.keySet())) despawn(id);
    }

    static List<Vec3> points(RouteDefinition route) {
        List<Vec3> out = new ArrayList<>();
        for (RouteDefinition.Waypoint w : route.points) if (w != null && w.pos != null) out.add(w.pos.center());
        return out;
    }
}

package dev.playtime.engine.monster;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Data-driven monster or NPC. Editable through the monster editor or JSON. */
public class MonsterDefinition {
    public Id id;
    public String displayName;
    public String description = "";
    public boolean hostile = true;
    public BehaviourProfile behaviour = BehaviourProfile.STALKER;
    public Set<MonsterTrait> traits = EnumSet.noneOf(MonsterTrait.class);

    // Presentation
    public Id model;
    public Id texture;
    public Id animationFile;
    /** Keys: idle, walk, run, sprint, turn, search, attack, intro, stunned, ambush, crawl, despawn. */
    public Map<String, String> animations = new LinkedHashMap<>();
    /** Keys: ambient, alert, chase, attack, footstep, stunned, intro. */
    public Map<String, Id> sounds = new LinkedHashMap<>();
    public Id ambientParticle;
    public int ambientParticleInterval = 10;
    public float width = 0.8f;
    public float height = 2.0f;

    // Perception
    public double visionRange = 24;
    public double fovDegrees = 110;
    public double hearingRange = 16;
    public boolean needsLineOfSight = true;
    /** How long the last seen position is remembered after losing sight. */
    public int memoryTicks = 100;
    /** Players closer than this are always detected (touch/smell). */
    public double proximityRange = 2.5;

    // Movement (blocks per tick, multiplied by the world's difficulty)
    public double walkSpeed = 0.18;
    public double chaseSpeed = 0.32;
    public double sprintSpeed = 0.42;
    public int sprintDurationTicks = 60;
    public int sprintCooldownTicks = 100;
    public double turnSpeedDegrees = 20;

    // Attack
    public double attackRange = 2.0;
    public int attackCooldownTicks = 30;
    public float attackDamage = 8;
    public CaptureOutcome captureOutcome = CaptureOutcome.CATCH;

    // Stun / distraction
    /** Hand abilities that stun this monster (for example {@code playtime:flare}). */
    public Set<Id> stunnedBy = new LinkedHashSet<>();
    public int stunTicks = 80;
    /** Noise tags this monster is drawn to (for example {@code playtime:toy_squeak}). */
    public Set<Id> distractedBy = new LinkedHashSet<>();

    // Searching
    public int investigateTicks = 120;
    public int searchTicks = 200;

    // Routes and recovery
    public Id patrolRoute;
    public RecoveryStrategy recovery = RecoveryStrategy.SKIP_WAYPOINT;
    /** Ticks without progress before counting as stuck. */
    public int stuckTicks = 60;
    /** Ticks between AI decisions (higher is cheaper). */
    public int thinkInterval = 4;

    // Spawning
    public List<Condition> spawnConditions = new ArrayList<>();
    public int maxInstances = 1;
    /** Despawn when this flag is set on the save (encounter finished). */
    public Id despawnFlag;
    public boolean despawnWhenChaseEnds = true;
    /** Story event fired the first time a player meets this monster. */
    public Id introEvent;
    public List<Action> onCatch = new ArrayList<>();

    // NPCs
    public Id dialogue;

    /** Profile-specific numbers: ambush_radius, chase_duration, watched_angle, retreat_distance... */
    public Map<String, Double> params = new LinkedHashMap<>();

    public double param(String key, double fallback) {
        Double v = params == null ? null : params.get(key);
        return v == null ? fallback : v;
    }

    public String animation(String key) {
        String v = animations == null ? null : animations.get(key);
        return v != null ? v : key;
    }

    public MonsterDefinition normalize() {
        if (displayName == null) displayName = id == null ? "Monster" : id.path();
        if (behaviour == null) behaviour = BehaviourProfile.STALKER;
        if (traits == null) traits = EnumSet.noneOf(MonsterTrait.class);
        if (animations == null) animations = new LinkedHashMap<>();
        if (sounds == null) sounds = new LinkedHashMap<>();
        if (stunnedBy == null) stunnedBy = new LinkedHashSet<>();
        if (distractedBy == null) distractedBy = new LinkedHashSet<>();
        if (spawnConditions == null) spawnConditions = new ArrayList<>();
        if (onCatch == null) onCatch = new ArrayList<>();
        if (params == null) params = new LinkedHashMap<>();
        if (recovery == null) recovery = RecoveryStrategy.SKIP_WAYPOINT;
        if (captureOutcome == null) captureOutcome = CaptureOutcome.CATCH;
        if (behaviour == BehaviourProfile.NPC) hostile = false;
        thinkInterval = Math.max(1, Math.min(20, thinkInterval));
        stuckTicks = Math.max(20, stuckTicks);
        maxInstances = Math.max(1, maxInstances);
        fovDegrees = Math.max(10, Math.min(360, fovDegrees));
        return this;
    }
}

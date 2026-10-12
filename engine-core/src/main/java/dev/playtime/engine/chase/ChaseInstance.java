package dev.playtime.engine.chase;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A running chase. Not saved: an interrupted chase restarts from its trigger after a reload. */
public final class ChaseInstance {
    public final ChaseDefinition definition;
    public final UUID triggeredBy;
    public final Set<UUID> participants = new LinkedHashSet<>();
    public final long startedTick;
    public int stage = -1;
    public long stageStartedTick;
    public long spawnAtTick;
    public UUID monster;
    public boolean monsterSpawned;

    ChaseInstance(ChaseDefinition definition, UUID triggeredBy, long tick) {
        this.definition = definition;
        this.triggeredBy = triggeredBy;
        this.startedTick = tick;
        this.spawnAtTick = tick + Math.max(0, definition.startDelayTicks);
    }

    public ChaseStage currentStage() {
        return stage >= 0 && stage < definition.stages.size() ? definition.stages.get(stage) : null;
    }
}

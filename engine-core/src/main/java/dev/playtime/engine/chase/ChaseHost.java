package dev.playtime.engine.chase;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.util.Id;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Engine services used by the chase controller. */
public interface ChaseHost {
    long tick();

    Facts facts(UUID player);

    void run(List<Action> actions, UUID player);

    /** Spawns the chase monster and locks it into the chase. Returns its instance id, or null on failure. */
    UUID spawnChaseMonster(ChaseDefinition chase, ChaseStage stage, UUID target);

    /** Changes the route/speed of a running chase monster when a stage starts. */
    void updateChaseMonster(UUID monster, ChaseDefinition chase, ChaseStage stage);

    void despawnMonster(UUID monster);

    /** Lets a chase monster return to its normal AI when the chase ends without despawning it. */
    void releaseMonster(UUID monster);

    boolean isMonsterAlive(UUID monster);

    void respawnAtCheckpoint(UUID player, Id checkpoint);

    void resetPuzzles(List<Id> puzzles);

    void markCompleted(ChaseDefinition chase, Collection<UUID> participants);

    void returnToMenu(UUID player);

    void notify(Collection<UUID> players, String objective, int ticksLeft);

    /** Online players within the participation radius of the trigger. */
    Collection<UUID> nearbyPlayers(UUID trigger);

    boolean failsForAll();
}

package dev.playtime.engine.engine;

import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.progress.ObjectiveStatus;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.Id;

import java.util.UUID;

/** Facts for one player's save (or world-only facts when {@code player} is null). */
final class PlayerFacts implements Facts {
    private final PlaytimeEngine engine;
    private final UUID player;
    private final PlayerProgress progress;

    PlayerFacts(PlaytimeEngine engine, UUID player) {
        this.engine = engine;
        this.player = player;
        this.progress = player == null ? engine.progression.isShared() ? engine.progression.sharedSlot().progress : null
                : engine.progression.progress(player).orElse(null);
    }

    private static boolean in(java.util.Set<Id> set, Id id) {
        return set != null && set.contains(id);
    }

    @Override public boolean flag(Id id) { return in(progress == null ? null : progress.flags, id); }

    @Override public boolean worldFlag(Id id) { return engine.state.worldFlags.contains(id); }

    @Override public boolean hasHand(Id id) { return in(progress == null ? null : progress.unlockedHands, id); }

    @Override
    public boolean selectedHand(Id id) {
        return progress != null && (id.equals(progress.selectedLeftHand) || id.equals(progress.selectedRightHand));
    }

    @Override
    public boolean hasKey(Id id) {
        if (in(progress == null ? null : progress.keys, id)) return true;
        return player != null && engine.bridge.heldCredentials(player).contains(id);
    }

    @Override
    public boolean puzzleCompleted(Id id) {
        return in(progress == null ? null : progress.completedPuzzles, id) || engine.puzzles.isCompleted(id);
    }

    @Override
    public boolean eventTriggered(Id id) {
        return in(progress == null ? null : progress.triggeredEvents, id) || engine.state.firedEvents.contains(id) && progress == null;
    }

    @Override
    public boolean objectiveCompleted(Id id) {
        return progress != null && progress.objectives.get(id) == ObjectiveStatus.COMPLETED;
    }

    @Override
    public boolean objectiveActive(Id id) {
        return progress != null && progress.objectives.get(id) == ObjectiveStatus.ACTIVE;
    }

    @Override public boolean chapterIs(Id id) { return progress != null && id.equals(progress.chapter); }

    @Override public boolean devicePowered(Id id) { return engine.power.isPowered(id); }

    @Override
    public boolean deviceActive(Id id) {
        return engine.graph.device(id).isPresent() && engine.graph.state(id).active;
    }

    @Override public boolean doorOpen(Id id) { return engine.doors.isOpen(id); }

    @Override
    public boolean chaseCompleted(Id id) {
        return in(progress == null ? null : progress.completedChases, id);
    }

    @Override public boolean hasCollectible(Id id) { return in(progress == null ? null : progress.collectibles, id); }

    @Override
    public boolean inArea(Box area) {
        if (player == null) return false;
        PlayerPosition p = engine.bridge.position(player);
        return p != null && area.contains(p.dimension(), p.x(), p.y(), p.z());
    }
}

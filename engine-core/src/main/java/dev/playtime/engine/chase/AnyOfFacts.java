package dev.playtime.engine.chase;

import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.Id;

import java.util.List;
import java.util.function.Predicate;

/** Combines several players' facts: a fact holds if it holds for any of them. */
final class AnyOfFacts implements Facts {
    private final List<Facts> all;

    AnyOfFacts(List<Facts> all) {
        this.all = all;
    }

    private boolean any(Predicate<Facts> p) {
        for (Facts f : all) if (p.test(f)) return true;
        return false;
    }

    @Override public boolean flag(Id id) { return any(f -> f.flag(id)); }
    @Override public boolean worldFlag(Id id) { return any(f -> f.worldFlag(id)); }
    @Override public boolean hasHand(Id id) { return any(f -> f.hasHand(id)); }
    @Override public boolean selectedHand(Id id) { return any(f -> f.selectedHand(id)); }
    @Override public boolean hasKey(Id id) { return any(f -> f.hasKey(id)); }
    @Override public boolean puzzleCompleted(Id id) { return any(f -> f.puzzleCompleted(id)); }
    @Override public boolean eventTriggered(Id id) { return any(f -> f.eventTriggered(id)); }
    @Override public boolean objectiveCompleted(Id id) { return any(f -> f.objectiveCompleted(id)); }
    @Override public boolean objectiveActive(Id id) { return any(f -> f.objectiveActive(id)); }
    @Override public boolean chapterIs(Id id) { return any(f -> f.chapterIs(id)); }
    @Override public boolean devicePowered(Id id) { return any(f -> f.devicePowered(id)); }
    @Override public boolean deviceActive(Id id) { return any(f -> f.deviceActive(id)); }
    @Override public boolean doorOpen(Id id) { return any(f -> f.doorOpen(id)); }
    @Override public boolean chaseCompleted(Id id) { return any(f -> f.chaseCompleted(id)); }
    @Override public boolean hasCollectible(Id id) { return any(f -> f.hasCollectible(id)); }
    @Override public boolean inArea(Box area) { return any(f -> f.inArea(area)); }
}

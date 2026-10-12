package dev.playtime.engine.logic;

import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.Id;

/** Read-only view of game state used to evaluate conditions for one player (or the world). */
public interface Facts {
    boolean flag(Id id);

    boolean worldFlag(Id id);

    boolean hasHand(Id id);

    boolean selectedHand(Id id);

    boolean hasKey(Id id);

    boolean puzzleCompleted(Id id);

    boolean eventTriggered(Id id);

    boolean objectiveCompleted(Id id);

    boolean objectiveActive(Id id);

    boolean chapterIs(Id id);

    boolean devicePowered(Id id);

    boolean deviceActive(Id id);

    boolean doorOpen(Id id);

    boolean chaseCompleted(Id id);

    boolean hasCollectible(Id id);

    boolean inArea(Box area);
}

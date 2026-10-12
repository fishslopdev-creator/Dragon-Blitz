package dev.playtime.engine.puzzle;

import com.google.gson.JsonObject;
import dev.playtime.engine.util.Id;

import java.util.Set;

/**
 * A puzzle template. Implementations are stateless; all per-puzzle state lives in
 * {@link PuzzleState#data} so it is saved and reloaded automatically.
 *
 * <p>To add a new puzzle type, implement this interface and register it with
 * {@link PuzzleTemplates#register}.
 */
public interface PuzzleLogic {
    Id id();

    /** Devices this template listens to, read from its settings. Used to route events. */
    Set<Id> devices(JsonObject settings);

    /** Called when the puzzle becomes active (and again after a reset). */
    default void start(PuzzleRun run) {}

    Outcome onEvent(PuzzleRun run, PuzzleEvent event);

    /** Only called for active puzzles when {@link #ticks(JsonObject)} is true. */
    default Outcome tick(PuzzleRun run) {
        return Outcome.CONTINUE;
    }

    default boolean ticks(JsonObject settings) {
        return false;
    }

    /** Checks settings and reports problems for the map validator. */
    default void validate(JsonObject settings, dev.playtime.engine.util.IssueList issues, String source) {}

    enum Outcome {
        CONTINUE,
        SUCCESS,
        FAILURE
    }
}

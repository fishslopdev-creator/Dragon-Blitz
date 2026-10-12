package dev.playtime.engine.puzzle;

import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.util.Id;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Engine services the puzzle engine depends on. */
public interface PuzzleHost extends PuzzleContext {
    Facts facts(UUID player);

    boolean hasItems(UUID player, Set<Id> items);

    void onStarted(PuzzleDefinition def, PuzzleState state, UUID player);

    void onCompleted(PuzzleDefinition def, PuzzleState state, UUID player);

    void onFailed(PuzzleDefinition def, PuzzleState state, boolean finalFailure);

    /** Clears device states and sockets of a puzzle that is being reset. */
    void resetDevices(Collection<Id> devices);

    /** Shows a status message to the given players. */
    void progress(PuzzleDefinition def, Collection<UUID> players, String message);
}

package dev.playtime.engine.progress;

import dev.playtime.engine.util.Id;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Story progress for one save. Used both for a player's own slots and for the
 * shared world save, depending on the world's progression mode.
 */
public class PlayerProgress {
    public Id chapter;
    public Id checkpoint;
    public Set<Id> unlockedHands = new LinkedHashSet<>();
    public Set<Id> keys = new LinkedHashSet<>();
    public Set<Id> collectibles = new LinkedHashSet<>();
    public Set<Id> completedPuzzles = new LinkedHashSet<>();
    public Set<Id> activatedMachinery = new LinkedHashSet<>();
    public Set<Id> openedDoors = new LinkedHashSet<>();
    public Set<Id> triggeredEvents = new LinkedHashSet<>();
    public Set<Id> encounteredMonsters = new LinkedHashSet<>();
    public Set<Id> completedChases = new LinkedHashSet<>();
    public Set<Id> discoveredLocations = new LinkedHashSet<>();
    public Set<Id> flags = new LinkedHashSet<>();
    public Set<Id> completedTutorials = new LinkedHashSet<>();
    public Map<Id, ObjectiveStatus> objectives = new LinkedHashMap<>();
    /** Unlocks gained since the last checkpoint; may be rolled back on death. */
    public Set<Id> handsSinceCheckpoint = new LinkedHashSet<>();
    public Set<Id> keysSinceCheckpoint = new LinkedHashSet<>();
    public Id grabPack;
    public Id selectedLeftHand;
    public Id selectedRightHand;
    public boolean gameCompleted;
    public Id endingReached;
    public int deaths;

    public boolean isStarted() {
        return checkpoint != null || !unlockedHands.isEmpty() || !completedPuzzles.isEmpty()
                || !triggeredEvents.isEmpty() || !collectibles.isEmpty() || gameCompleted;
    }

    public boolean hasHand(Id hand) {
        return unlockedHands.contains(hand);
    }

    public void unlockHand(Id hand) {
        if (unlockedHands.add(hand)) handsSinceCheckpoint.add(hand);
    }

    public void addKey(Id key) {
        if (keys.add(key)) keysSinceCheckpoint.add(key);
    }

    public void reachCheckpoint(Id id) {
        checkpoint = id;
        handsSinceCheckpoint.clear();
        keysSinceCheckpoint.clear();
    }

    public PlayerProgress normalize() {
        if (unlockedHands == null) unlockedHands = new LinkedHashSet<>();
        if (keys == null) keys = new LinkedHashSet<>();
        if (collectibles == null) collectibles = new LinkedHashSet<>();
        if (completedPuzzles == null) completedPuzzles = new LinkedHashSet<>();
        if (activatedMachinery == null) activatedMachinery = new LinkedHashSet<>();
        if (openedDoors == null) openedDoors = new LinkedHashSet<>();
        if (triggeredEvents == null) triggeredEvents = new LinkedHashSet<>();
        if (encounteredMonsters == null) encounteredMonsters = new LinkedHashSet<>();
        if (completedChases == null) completedChases = new LinkedHashSet<>();
        if (discoveredLocations == null) discoveredLocations = new LinkedHashSet<>();
        if (flags == null) flags = new LinkedHashSet<>();
        if (completedTutorials == null) completedTutorials = new LinkedHashSet<>();
        if (objectives == null) objectives = new LinkedHashMap<>();
        if (handsSinceCheckpoint == null) handsSinceCheckpoint = new LinkedHashSet<>();
        if (keysSinceCheckpoint == null) keysSinceCheckpoint = new LinkedHashSet<>();
        return this;
    }
}

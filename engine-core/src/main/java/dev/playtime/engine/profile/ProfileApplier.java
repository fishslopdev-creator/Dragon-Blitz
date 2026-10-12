package dev.playtime.engine.profile;

import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;

/** Applies a profile's starting state to fresh progress and produces the item kit. */
public final class ProfileApplier {
    private ProfileApplier() {}

    public static StartingKit apply(PlayerProfile profile, PlayerProgress progress) {
        profile.normalize();
        if (profile.giveGrabPack) progress.grabPack = profile.grabPack;
        for (Id hand : profile.startingHands) progress.unlockedHands.add(hand);
        progress.selectedLeftHand = pick(profile.startingLeftHand, progress);
        progress.selectedRightHand = pick(profile.startingRightHand, progress);
        progress.keys.addAll(profile.keys);
        progress.completedPuzzles.addAll(profile.completedPuzzles);
        progress.openedDoors.addAll(profile.doorAccess);
        progress.flags.addAll(profile.flags);
        if (profile.startingChapter != null) progress.chapter = profile.startingChapter;
        if (profile.startingCheckpoint != null) progress.reachCheckpoint(profile.startingCheckpoint);
        // Starting unlocks are part of the baseline and never rolled back on death.
        progress.handsSinceCheckpoint.clear();
        progress.keysSinceCheckpoint.clear();
        List<ItemSpec> items = new ArrayList<>(profile.inventory);
        return new StartingKit(profile.giveGrabPack ? profile.grabPack : null, items,
                new ArrayList<>(profile.keys), profile.batteries, profile.canSprint, profile.canCrouch);
    }

    private static Id pick(Id requested, PlayerProgress progress) {
        return requested != null && progress.unlockedHands.contains(requested) ? requested : null;
    }
}

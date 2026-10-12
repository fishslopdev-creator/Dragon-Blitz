package dev.playtime.engine.puzzle;

import dev.playtime.engine.util.Id;

import java.util.Set;
import java.util.UUID;

/**
 * Something that happened to a device a puzzle watches.
 *
 * @param device  the device (or puzzle id for PUZZLE_COMPLETED)
 * @param on      new active/powered state, where relevant
 * @param item    inserted item, swiped credential or scanning hand
 * @param slot    socket name for SOCKET_CHANGED
 * @param tags    interaction tags of the scanning hand, or nothing
 * @param level   clearance of a swiped card
 */
public record PuzzleEvent(PuzzleEventType type, Id device, boolean on, Id item, String slot,
                          Set<Id> tags, int level, UUID player) {

    public static PuzzleEvent device(PuzzleEventType type, Id device, boolean on, UUID player) {
        return new PuzzleEvent(type, device, on, null, null, Set.of(), 0, player);
    }

    public static PuzzleEvent socket(Id device, String slot, Id item, UUID player) {
        return new PuzzleEvent(PuzzleEventType.SOCKET_CHANGED, device, item != null, item, slot, Set.of(), 0, player);
    }

    public static PuzzleEvent card(Id reader, Id credential, int clearance, UUID player) {
        return new PuzzleEvent(PuzzleEventType.CARD_SWIPED, reader, true, credential, null, Set.of(), clearance, player);
    }

    public static PuzzleEvent scan(Id scanner, Id hand, Set<Id> tags, UUID player) {
        return new PuzzleEvent(PuzzleEventType.HAND_SCANNED, scanner, true, hand, null, tags, 0, player);
    }

    public static PuzzleEvent reached(Id marker, UUID player) {
        return new PuzzleEvent(PuzzleEventType.PLAYER_REACHED, marker, true, null, null, Set.of(), 0, player);
    }
}

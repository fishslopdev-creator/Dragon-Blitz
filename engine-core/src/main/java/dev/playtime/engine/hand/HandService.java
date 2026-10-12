package dev.playtime.engine.hand;

import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.progress.ProgressionService;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IdSets;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative hand unlocks, pickups and selection. */
public final class HandService {
    private final HandRegistry registry;
    private final ProgressionService progression;

    public HandService(HandRegistry registry, ProgressionService progression) {
        this.registry = registry;
        this.progression = progression;
    }

    public HandRegistry registry() {
        return registry;
    }

    public record PickupResult(boolean granted, boolean alreadyOwned, boolean removePickup, boolean grantToAll,
                               HandDefinition hand, String message) {
        static PickupResult refused(String message) {
            return new PickupResult(false, false, false, false, null, message);
        }
    }

    /**
     * Collect a hand pickup. Order: already unlocked? -> unlock conditions -> grant ->
     * mark the pickup used -> auto-equip. The caller plays the animation, shows the
     * notification, fires {@link HandPickupConfig#event} and, if {@code grantToAll},
     * calls {@link #grant} for every other online player.
     */
    public PickupResult collect(UUID player, HandPickupConfig pickup) {
        if (pickup == null || pickup.hand == null) return PickupResult.refused("This pickup has no hand configured.");
        Optional<HandDefinition> defOpt = registry.get(pickup.hand);
        if (defOpt.isEmpty()) return PickupResult.refused("Unknown hand " + pickup.hand + ".");
        HandDefinition def = defOpt.get();
        PlayerProgress p = progression.progressOrCreate(player);
        Id pickupFlag = pickupFlag(pickup);

        if (pickup.mode == PickupMode.PERMANENT && pickupFlag != null && p.flags.contains(pickupFlag)) {
            return new PickupResult(false, true, true, false, def, null);
        }
        if (pickup.mode == PickupMode.CHAPTER_LIMITED && pickup.chapter != null && !pickup.chapter.equals(p.chapter)) {
            return PickupResult.refused("You can't take this yet.");
        }
        String locked = checkUnlock(def.unlock, p);
        if (locked != null) return PickupResult.refused(locked);

        boolean already = p.hasHand(def.id);
        if (!already) grant(player, def.id, pickup.autoEquip);
        if (pickup.mode == PickupMode.PERMANENT && pickupFlag != null) p.flags.add(pickupFlag);
        progression.markDirty(player);
        boolean remove = pickup.mode == PickupMode.PERMANENT;
        String message = already ? null : def.displayName + " unlocked";
        return new PickupResult(!already, already, remove, pickup.mode == PickupMode.SHARED_ALL, def, message);
    }

    /** Unlocks a hand directly (admin command, story action, shared pickup). Returns false if already owned. */
    public boolean grant(UUID player, Id hand, boolean equip) {
        HandDefinition def = registry.get(hand).orElseThrow(() -> new IllegalArgumentException("Unknown hand " + hand));
        PlayerProgress p = progression.progressOrCreate(player);
        if (p.hasHand(hand)) return false;
        p.unlockHand(hand);
        if (equip) {
            LauncherSide side = def.launcher == LauncherSide.LEFT ? LauncherSide.LEFT : LauncherSide.RIGHT;
            if (def.fitsGrabPack(p.grabPack)) setSelected(p, side, hand);
        } else if (def.replaces != null) {
            if (def.replaces.equals(p.selectedRightHand)) p.selectedRightHand = hand;
            if (def.replaces.equals(p.selectedLeftHand)) p.selectedLeftHand = hand;
        }
        progression.markDirty(player);
        return true;
    }

    public boolean revoke(UUID player, Id hand) {
        PlayerProgress p = progression.progressOrCreate(player);
        boolean removed = p.unlockedHands.remove(hand);
        p.handsSinceCheckpoint.remove(hand);
        if (hand.equals(p.selectedLeftHand)) p.selectedLeftHand = null;
        if (hand.equals(p.selectedRightHand)) p.selectedRightHand = null;
        if (removed) progression.markDirty(player);
        return removed;
    }

    public record SelectResult(boolean ok, String message) {}

    /** Validates a hand-wheel choice sent by the client. Only unlocked, compatible hands are accepted. */
    public SelectResult select(UUID player, LauncherSide side, Id hand) {
        if (side == null || side == LauncherSide.EITHER) return new SelectResult(false, "Invalid launcher");
        PlayerProgress p = progression.progressOrCreate(player);
        if (p.grabPack == null) return new SelectResult(false, "You don't have a GrabPack.");
        if (hand == null) {
            setSelected(p, side, null);
            progression.markDirty(player);
            return new SelectResult(true, null);
        }
        Optional<HandDefinition> def = registry.get(hand);
        if (def.isEmpty()) return new SelectResult(false, "Unknown hand");
        if (!p.hasHand(hand)) return new SelectResult(false, "That hand is locked.");
        if (!def.get().launcher.accepts(side)) return new SelectResult(false, "That hand doesn't fit this launcher.");
        if (!def.get().fitsGrabPack(p.grabPack)) return new SelectResult(false, "That hand doesn't fit this GrabPack.");
        setSelected(p, side, hand);
        progression.markDirty(player);
        return new SelectResult(true, null);
    }

    public Optional<HandStats> selectedStats(UUID player, LauncherSide side) {
        PlayerProgress p = progression.progressOrCreate(player);
        Id id = side == LauncherSide.LEFT ? p.selectedLeftHand : p.selectedRightHand;
        return registry.get(id).map(def -> HandStats.of(def, p.flags));
    }

    /** Wheel contents for one launcher. Hands replaced by an unlocked upgrade are hidden. */
    public List<WheelEntry> wheel(UUID player, LauncherSide side) {
        PlayerProgress p = progression.progressOrCreate(player);
        Id selected = side == LauncherSide.LEFT ? p.selectedLeftHand : p.selectedRightHand;
        List<WheelEntry> out = new ArrayList<>();
        for (HandDefinition def : registry.sorted()) {
            if (!def.launcher.accepts(side) || !def.fitsGrabPack(p.grabPack)) continue;
            if (isReplaced(def, p.unlockedHands)) continue;
            boolean unlocked = p.hasHand(def.id);
            List<String> abilities = new ArrayList<>();
            if (unlocked) for (Id a : HandStats.of(def, p.flags).abilities()) abilities.add(a.path());
            out.add(new WheelEntry(def.id, def.displayName, unlocked ? def.description : "???", def.icon,
                    def.color, unlocked, def.id.equals(selected), abilities));
        }
        return out;
    }

    private boolean isReplaced(HandDefinition def, Set<Id> unlocked) {
        for (HandDefinition other : registry.all()) {
            if (def.id.equals(other.replaces) && unlocked.contains(other.id)) return true;
        }
        return false;
    }

    static String checkUnlock(UnlockCondition u, PlayerProgress p) {
        if (u == null) return null;
        boolean ok = (u.chapters == null || u.chapters.isEmpty() || p.chapter != null && u.chapters.contains(p.chapter))
                && IdSets.containsAll(p.flags, u.flags)
                && IdSets.containsAll(p.unlockedHands, u.hands)
                && IdSets.containsAll(p.completedPuzzles, u.puzzles);
        if (ok) return null;
        return u.lockedMessage != null ? u.lockedMessage : "You can't take this yet.";
    }

    private static Id pickupFlag(HandPickupConfig pickup) {
        return pickup.id == null ? null : new Id(pickup.id.namespace(), "pickup/" + pickup.id.path());
    }

    private static void setSelected(PlayerProgress p, LauncherSide side, Id hand) {
        if (side == LauncherSide.LEFT) p.selectedLeftHand = hand; else p.selectedRightHand = hand;
    }
}

package dev.playtime.engine.profile;

import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A named starting kit and permission set edited through the Player Setup Console.
 * {@code null} on a Boolean override means "use the world rule".
 */
public class PlayerProfile {
    public Id id;
    public String displayName = "Player";
    public boolean giveGrabPack = true;
    public Id grabPack = Id.of("grabpack");
    public Set<Id> startingHands = new LinkedHashSet<>();
    public Id startingLeftHand;
    public Id startingRightHand;
    public List<ItemSpec> inventory = new ArrayList<>();
    public Set<Id> keys = new LinkedHashSet<>();
    public int batteries;
    public Id startingChapter;
    public Id startingCheckpoint;
    public Set<Id> completedPuzzles = new LinkedHashSet<>();
    public Set<Id> doorAccess = new LinkedHashSet<>();
    public Set<Id> flags = new LinkedHashSet<>();
    public boolean canSprint = true;
    public boolean canCrouch = true;
    public Set<Id> abilities = new LinkedHashSet<>();
    public Boolean showMainMenu;
    public Boolean keepItemsOnDeath;
    public Boolean keepHandsOnDeath;
    public Boolean sharedProgression;
    public boolean tutorial = true;
    /** Systems (device types or specific device ids) this player may not interact with. */
    public Set<Id> deniedInteractions = new LinkedHashSet<>();
    /** Free-form map-specific settings for addons. */
    public Map<String, String> extra = new LinkedHashMap<>();

    public PlayerProfile normalize() {
        if (startingHands == null) startingHands = new LinkedHashSet<>();
        if (inventory == null) inventory = new ArrayList<>();
        inventory.removeIf(i -> i == null || i.item == null || i.count <= 0);
        for (ItemSpec spec : inventory) spec.count = Math.min(spec.count, 64 * 36);
        if (keys == null) keys = new LinkedHashSet<>();
        if (completedPuzzles == null) completedPuzzles = new LinkedHashSet<>();
        if (doorAccess == null) doorAccess = new LinkedHashSet<>();
        if (flags == null) flags = new LinkedHashSet<>();
        if (abilities == null) abilities = new LinkedHashSet<>();
        if (deniedInteractions == null) deniedInteractions = new LinkedHashSet<>();
        if (extra == null) extra = new LinkedHashMap<>();
        batteries = Math.max(0, batteries);
        if (displayName == null) displayName = id == null ? "Player" : id.path();
        return this;
    }

    public boolean mayInteractWith(Id deviceType, Id deviceId) {
        return !(deniedInteractions.contains(deviceType) || deviceId != null && deniedInteractions.contains(deviceId));
    }
}

package dev.playtime.engine.engine;

import dev.playtime.engine.access.AccessResult;
import dev.playtime.engine.access.AccessRule;
import dev.playtime.engine.access.CredentialDefinition;
import dev.playtime.engine.access.DoorDefinition;
import dev.playtime.engine.access.DoorOperation;
import dev.playtime.engine.access.DoorState;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceState;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.hand.HandPickupConfig;
import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.ActionType;
import dev.playtime.engine.logic.TriggerType;
import dev.playtime.engine.power.PowerNetwork;
import dev.playtime.engine.progress.ObjectiveStatus;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Player interactions with placed engine blocks: switches, readers, sockets, doors,
 * trigger volumes and pickups. The game layer calls these from block use/step handlers.
 */
public final class DeviceInteractions {
    private final PlaytimeEngine e;

    DeviceInteractions(PlaytimeEngine engine) {
        this.e = engine;
    }

    // ------------------------------------------------------------------ device registration

    /** Registers a newly placed engine block. Returns its id (generated unless {@code requested} is free). */
    public Id place(DeviceType type, BlockRef pos, Id requested, Map<String, String> settings) {
        Optional<Device> existing = e.graph.deviceAt(pos);
        if (existing.isPresent()) return existing.get().id;
        Id id = requested != null && e.graph.device(requested).isEmpty() ? requested
                : e.graph.freshDeviceId(Id.DEFAULT_NAMESPACE, type.name().toLowerCase());
        Device d = new Device(id, type, pos);
        if (settings != null) d.settings.putAll(settings);
        e.graph.addDevice(d);
        e.power.markDirty();
        return id;
    }

    /** Block broken: removes the device and its links. */
    public List<Connection> breakAt(BlockRef pos) {
        Optional<Device> d = e.graph.deviceAt(pos);
        if (d.isEmpty()) return List.of();
        List<Connection> removed = e.graph.removeDevice(d.get().id);
        e.power.markDirty();
        return removed;
    }

    /** Device GUI saved new settings (operators only; checked by the caller). */
    public boolean configure(Id device, String name, Map<String, String> settings, int inputsRequired, boolean latch) {
        Device d = e.graph.device(device).orElse(null);
        if (d == null) return false;
        d.name = name;
        d.settings.clear();
        if (settings != null) d.settings.putAll(settings);
        d.inputsRequired = Math.max(1, inputsRequired);
        d.latch = latch;
        e.power.markDirty();
        e.bridge.deviceChanged(d, e.graph.state(device));
        return true;
    }

    /** Connection Device: creates a link after validation (loops, missing devices, type mismatches). */
    public IssueList connect(Connection c) {
        IssueList issues = e.graph.connect(c);
        if (!issues.hasErrors()) e.power.markDirty();
        return issues;
    }

    public boolean disconnect(Id connection) {
        boolean removed = e.graph.removeConnection(connection);
        if (removed) e.power.markDirty();
        return removed;
    }

    /** "Test" button in the Connection Device preview: pulses the source as an operator would. */
    public void test(Id device) {
        e.signals.pulse(device, null);
    }

    // ------------------------------------------------------------------ use

    /**
     * Right-click on an engine block. {@code heldItem} is the item id in the player's hand
     * (for sockets and readers), or null.
     */
    public void use(UUID player, Id deviceId, Id heldItem) {
        Device d = e.graph.device(deviceId).orElse(null);
        if (d == null) return;
        DeviceState s = e.graph.state(deviceId);
        if (s.disabled) {
            e.bridge.showStatus(player, "It doesn't respond.");
            return;
        }
        if (!e.mayInteract(player, d)) {
            e.bridge.showStatus(player, "You can't use that.");
            return;
        }
        switch (d.type) {
            case POWER_SWITCH, LEVER -> e.signals.setActive(d.id, !s.active, player);
            case BUTTON -> e.signals.pulse(d.id, player);
            case TIMED_SWITCH -> {
                if (!s.active) {
                    e.signals.setActive(d.id, true, player);
                    Action off = Action.of(ActionType.SET_DEVICE, d.id);
                    off.on = false;
                    off.delay = Math.max(1, d.intSetting("duration", 100));
                    e.actions.run(List.of(off), player);
                }
            }
            case GENERATOR, FUSE_BOX, PART_SOCKET, MACHINE, CHARGING_STATION, BATTERY_DEVICE -> {
                if (heldItem != null && insert(player, d, heldItem)) return;
                if (d.type == DeviceType.GENERATOR || d.type == DeviceType.MACHINE) {
                    if (!PowerNetwork.socketsSatisfied(d, s)) {
                        e.bridge.showStatus(player, "It's missing parts: " + missing(d, s));
                    } else {
                        e.signals.setActive(d.id, !s.active, player);
                    }
                } else if (!PowerNetwork.socketsSatisfied(d, s)) {
                    e.bridge.showStatus(player, "Empty slots: " + missing(d, s));
                }
            }
            case KEYCARD_READER, ACCESS_TERMINAL, SECURITY_TERMINAL -> reader(player, d);
            case DOOR_CONTROLLER -> {
                Id door = d.idSetting("door");
                if (door != null) tryOpenDoor(player, door);
            }
            case HAND_SCANNER -> e.bridge.showStatus(player, "Fire a hand at the scanner.");
            case CHECKPOINT -> e.setCheckpoint(player, d.id, true);
            case HAND_PICKUP -> pickup(player, d);
            case CHAPTER_TRANSITION -> {
                Id chapter = d.idSetting("chapter");
                if (chapter != null) e.startChapter(player, chapter);
            }
            case STORY_TRIGGER -> {
                Id event = d.idSetting("event");
                if (event != null) e.story.fire(event, player, false);
                Id collectible = d.idSetting("collectible");
                if (collectible != null) e.collect(player, collectible, true);
                Id dialogue = d.idSetting("dialogue");
                if (dialogue != null) e.startDialogue(player, dialogue);
            }
            default -> e.signals.pulse(d.id, player);
        }
    }

    private boolean insert(UUID player, Device d, Id item) {
        DeviceState s = e.graph.state(d.id);
        for (Map.Entry<String, Id> slot : PowerNetwork.requiredSockets(d).entrySet()) {
            if (s.sockets.get(slot.getKey()) == null && slot.getValue().equals(item)) {
                s.sockets.put(slot.getKey(), item);
                e.power.markDirty();
                e.bridge.deviceChanged(d, s);
                e.bridge.playSound(null, Id.of("socket.insert"), d.pos, 1f);
                e.puzzles.dispatch(PuzzleEvent.socket(d.id, slot.getKey(), item, player));
                e.bridge.consumeHeldItem(player, item);
                return true;
            }
        }
        return false;
    }

    private static String missing(Device d, DeviceState s) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Id> slot : PowerNetwork.requiredSockets(d).entrySet()) {
            if (!slot.getValue().equals(s.sockets.get(slot.getKey()))) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(slot.getValue().path().replace('_', ' '));
            }
        }
        return sb.toString();
    }

    private void reader(UUID player, Device reader) {
        Id doorId = reader.idSetting("door");
        DoorDefinition door = doorId == null ? null : e.doors.get(doorId).orElse(null);
        AccessRule rule = door != null ? door.access : readerRule(reader);
        AccessResult result = e.access.evaluate(rule, e.accessRequest(player, null, reader.id));
        Id swiped = result.consumedCredential();
        if (swiped == null) swiped = bestCredential(player, rule);
        int clearance = swiped == null ? 0 : e.credentials.getOrDefault(swiped, new CredentialDefinition()).clearance;
        e.puzzles.dispatch(PuzzleEvent.card(reader.id, swiped, clearance, player));
        if (!result.granted()) {
            e.bridge.showStatus(player, result.message());
            e.bridge.playSound(null, Id.of("reader.deny"), reader.pos, 1f);
            return;
        }
        consume(player, result.consumedCredential());
        e.bridge.playSound(null, Id.of("reader.accept"), reader.pos, 1f);
        e.signals.pulse(reader.id, player);
        if (reader.latch) e.signals.setActive(reader.id, true, player);
        if (door != null) e.openDoorFor(player, door.id);
    }

    private AccessRule readerRule(Device reader) {
        AccessRule rule = new AccessRule();
        for (String c : reader.setting("credentials", "").split(",")) {
            Id id = Id.tryParse(c.trim());
            if (id != null) rule.credentials.add(id);
        }
        rule.minClearance = reader.intSetting("clearance", 0);
        rule.consumeCredential = "true".equals(reader.setting("consume", "false"));
        rule.requiresPower = "true".equals(reader.setting("requires_power", "false"));
        return rule;
    }

    private Id bestCredential(UUID player, AccessRule rule) {
        var req = e.accessRequest(player, null, null);
        for (Id id : rule.credentials) if (req.heldCredentials().contains(id)) return id;
        Id best = null;
        int bestLevel = -1;
        for (Id id : req.heldCredentials()) {
            CredentialDefinition c = e.credentials.get(id);
            if (c != null && c.clearance > bestLevel) {
                bestLevel = c.clearance;
                best = id;
            }
        }
        return best;
    }

    private void consume(UUID player, Id credential) {
        if (credential == null) return;
        if (!e.bridge.removeCredentialItem(player, credential)) {
            PlayerProgress p = e.progression.progressOrCreate(player);
            p.keys.remove(credential);
            p.keysSinceCheckpoint.remove(credential);
            e.progression.markDirty(player);
        }
    }

    private void pickup(UUID player, Device d) {
        Id pickupId = d.idSetting("pickup");
        HandPickupConfig cfg = pickupId == null ? null : e.content.<HandPickupConfig>get(ContentKind.PICKUPS, pickupId).orElse(null);
        if (cfg == null) {
            Id hand = d.idSetting("hand");
            if (hand == null) return;
            cfg = new HandPickupConfig();
            cfg.id = d.id;
            cfg.hand = hand;
            String mode = d.setting("mode", "permanent");
            try {
                cfg.mode = dev.playtime.engine.hand.PickupMode.valueOf(mode.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                cfg.mode = dev.playtime.engine.hand.PickupMode.PERMANENT;
            }
            cfg.chapter = d.idSetting("chapter");
            cfg.event = d.idSetting("event");
        }
        e.sessions.pickupHand(player, cfg);
    }

    // ------------------------------------------------------------------ doors

    /** Player tries to open a door directly (door block or controller). */
    public AccessResult tryOpenDoor(UUID player, Id doorId) {
        DoorDefinition door = e.doors.get(doorId).orElse(null);
        if (door == null) return new AccessResult(false, null, List.of("Unknown door."));
        DoorState state = e.doors.state(doorId);
        if (state.forcedLocked) {
            e.bridge.showStatus(player, "It's locked tight.");
            e.bridge.playSound(null, door.deniedSound, door.anchor, 1f);
            return new AccessResult(false, null, List.of("It's locked tight."));
        }
        if (door.effectiveOperation() != DoorOperation.PLAYER) {
            String msg = door.effectiveOperation() == DoorOperation.POWER ? "It needs power." : "It won't open from here.";
            e.bridge.showStatus(player, msg);
            return new AccessResult(false, null, List.of(msg));
        }
        if (state.open) {
            if (door.openTicks == 0) e.doors.close(doorId);
            return new AccessResult(true, null, List.of());
        }
        PlayerProgress p = e.progression.progressOrCreate(player);
        if (door.permanentUnlock && p.openedDoors.contains(doorId)) {
            e.doors.open(doorId, e.tick);
            return new AccessResult(true, null, List.of());
        }
        Id powerDevice = door.anchor == null ? null : e.graph.deviceAt(door.anchor).map(d -> d.id).orElse(null);
        AccessResult result = e.access.evaluate(door.access, e.accessRequest(player, null, powerDevice));
        if (!result.granted()) {
            e.bridge.showStatus(player, result.message());
            e.bridge.playSound(null, door.deniedSound, door.anchor, 1f);
            return result;
        }
        consume(player, result.consumedCredential());
        e.openDoorFor(player, doorId);
        return result;
    }

    // ------------------------------------------------------------------ volumes

    /**
     * A player stepped into a trigger block or invisible volume. Each volume fires once per
     * save unless its {@code repeat} setting is true.
     */
    public void enter(UUID player, Id deviceId) {
        Device d = e.graph.device(deviceId).orElse(null);
        if (d == null || e.graph.state(deviceId).disabled) return;
        PlayerProgress p = e.progression.progressOrCreate(player);
        Id visited = new Id(d.id.namespace(), "visited/" + d.id.path());
        boolean repeat = "true".equals(d.setting("repeat", "false"));
        if (!repeat && p.flags.contains(visited)) return;
        if (!repeat) {
            p.flags.add(visited);
            e.progression.markDirty(player);
        }
        e.puzzles.dispatch(PuzzleEvent.reached(d.id, player));
        Id location = d.idSetting("location");
        if (location != null && p.discoveredLocations.add(location)) e.progression.markDirty(player);
        switch (d.type) {
            case CHASE_TRIGGER -> {
                Id chase = d.idSetting("chase");
                if (chase != null) e.chases.start(chase, player, false);
            }
            case STORY_TRIGGER -> {
                Id event = d.idSetting("event");
                if (event != null) e.story.fire(event, player, false);
            }
            case OBJECTIVE_TRIGGER -> {
                Id objective = d.idSetting("objective");
                if (objective != null) {
                    boolean complete = "complete".equals(d.setting("mode", "set"));
                    Action a = Action.of(complete ? ActionType.OBJECTIVE_COMPLETE : ActionType.OBJECTIVE_SET, objective);
                    e.actions.run(List.of(a), player);
                }
            }
            case CHECKPOINT -> e.setCheckpoint(player, d.id, true);
            case CHAPTER_TRANSITION -> {
                Id chapter = d.idSetting("chapter");
                if (chapter != null) e.startChapter(player, chapter);
            }
            default -> { }
        }
        e.signals.pulse(d.id, player);
        e.trigger(TriggerType.ENTER_AREA, d.id, player);
    }

    /** Area-based story events (configured with a box instead of a block). Call every ~10 ticks per player. */
    public void checkAreas(UUID player) {
        PlayerPosition pos = e.bridge.position(player);
        if (pos == null) return;
        for (var ev : e.story.withTrigger(TriggerType.ENTER_AREA)) {
            if (ev.trigger.area != null && ev.trigger.area.contains(pos.dimension(), pos.x(), pos.y(), pos.z())) {
                e.story.fire(ev.id, player, false);
            }
        }
        for (var chase : e.chases.definitions()) {
            if (chase.trigger.type == TriggerType.ENTER_AREA && chase.trigger.area != null
                    && chase.trigger.area.contains(pos.dimension(), pos.x(), pos.y(), pos.z())
                    && !e.chases.isActive(chase.id)) {
                e.chases.start(chase.id, player, false);
            }
        }
    }

    /** A key item (card/key) was picked up as a world item. */
    public void onCredentialPickedUp(UUID player, Id credential) {
        CredentialDefinition def = e.credentials.get(credential);
        if (def != null && def.trackInProgress) {
            e.progression.progressOrCreate(player).addKey(credential);
            e.progression.markDirty(player);
        }
        e.trigger(TriggerType.ITEM_PICKUP, credential, player);
    }

    /** Any other tracked item picked up (fuses, machine parts...). */
    public void onItemPickedUp(UUID player, Id item) {
        e.trigger(TriggerType.ITEM_PICKUP, item, player);
    }

    public void setObjective(UUID player, Id objective, ObjectiveStatus status) {
        Action a = Action.of(switch (status) {
            case ACTIVE -> ActionType.OBJECTIVE_SET;
            case COMPLETED -> ActionType.OBJECTIVE_COMPLETE;
            case FAILED -> ActionType.OBJECTIVE_FAIL;
        }, objective);
        e.actions.run(List.of(a), player);
    }
}

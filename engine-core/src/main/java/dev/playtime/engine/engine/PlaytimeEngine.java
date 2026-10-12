package dev.playtime.engine.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.playtime.engine.access.AccessEvaluator;
import dev.playtime.engine.access.AccessRequest;
import dev.playtime.engine.access.AccessRule;
import dev.playtime.engine.access.CredentialDefinition;
import dev.playtime.engine.access.DoorDefinition;
import dev.playtime.engine.access.DoorService;
import dev.playtime.engine.chase.ChaseController;
import dev.playtime.engine.chase.ChaseDefinition;
import dev.playtime.engine.chase.ChaseHost;
import dev.playtime.engine.chase.ChaseStage;
import dev.playtime.engine.config.WorldConfig;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.content.ContentLoader;
import dev.playtime.engine.content.ContentPack;
import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.ConnectionGraph;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceState;
import dev.playtime.engine.graph.Signal;
import dev.playtime.engine.graph.SignalBus;
import dev.playtime.engine.graph.SignalHandler;
import dev.playtime.engine.hand.HandDefinition;
import dev.playtime.engine.hand.HandRegistry;
import dev.playtime.engine.hand.HandService;
import dev.playtime.engine.hand.HandStats;
import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.logic.TriggerType;
import dev.playtime.engine.monster.MonsterDefinition;
import dev.playtime.engine.monster.MonsterRegistry;
import dev.playtime.engine.monster.RouteDefinition;
import dev.playtime.engine.power.PowerNetwork;
import dev.playtime.engine.power.PowerResult;
import dev.playtime.engine.profile.PlayerProfile;
import dev.playtime.engine.profile.ProfileAssignment;
import dev.playtime.engine.profile.ProfileRegistry;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.progress.PlayerSaveStore;
import dev.playtime.engine.progress.ProgressionService;
import dev.playtime.engine.progress.SaveMigrator;
import dev.playtime.engine.puzzle.PuzzleDefinition;
import dev.playtime.engine.puzzle.PuzzleEngine;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.puzzle.PuzzleEventType;
import dev.playtime.engine.puzzle.PuzzleHost;
import dev.playtime.engine.puzzle.PuzzleState;
import dev.playtime.engine.puzzle.PuzzleTemplates;
import dev.playtime.engine.menu.MenuTriggerTracker;
import dev.playtime.engine.story.DialogueSession;
import dev.playtime.engine.story.DialogueTree;
import dev.playtime.engine.story.StoryEngine;
import dev.playtime.engine.story.StoryEvent;
import dev.playtime.engine.util.AtomicFiles;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;
import dev.playtime.engine.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One running horror game: the engine instance for a single Minecraft world.
 *
 * <p>Files live in {@code <world>/playtime/}:
 * <ul>
 *   <li>{@code world_config.json} - title, menu, rules (World Settings screen)</li>
 *   <li>{@code world_state.json} - devices, links, doors, puzzles, shared save</li>
 *   <li>{@code players/<uuid>.json} - per-player saves</li>
 *   <li>{@code content/<kind>/*.json} - map definitions (hands, doors, puzzles, chases...)</li>
 * </ul>
 * Because every path is under the world folder, configuration never leaks between worlds.
 */
public final class PlaytimeEngine implements PuzzleHost, ChaseHost, SignalHandler {
    private static final Logger LOG = Logger.getLogger("PlaytimeEngine");
    /** Pseudo-player used for world-level actions in shared progression. */
    public static final UUID WORLD = new UUID(0, 0);
    public static final long AUTOSAVE_TICKS = 20 * 60;

    final Path root;
    final GameBridge bridge;
    WorldConfig config;
    WorldState state;
    ContentPack content;
    final IssueList loadIssues = new IssueList();

    final PlayerSaveStore saves;
    final ProgressionService progression;
    final HandRegistry hands = new HandRegistry();
    final HandService handService;
    final Map<Id, CredentialDefinition> credentials = new LinkedHashMap<>();
    final AccessEvaluator access = new AccessEvaluator(credentials);
    final DoorService doors;
    final ConnectionGraph graph;
    final SignalBus signals;
    final PowerNetwork power;
    final PuzzleEngine puzzles;
    final MonsterRegistry monsters = new MonsterRegistry();
    final MonsterDirector director;
    final ChaseController chases;
    final StoryEngine story;
    final DialogueSession dialogues;
    final ProfileRegistry profiles = new ProfileRegistry();
    final MenuTriggerTracker menus = new MenuTriggerTracker();
    final ActionRunner actions;
    final GrabPackService grabPack;
    final PlayerSessions sessions;
    final DeviceInteractions interactions;
    long tick;
    private long lastAutosave;
    private boolean stateDirty;
    private boolean runtimeRestored;

    private PlaytimeEngine(Path root, GameBridge bridge, WorldConfig config, WorldState state, ContentPack content,
                           PuzzleTemplates templates) {
        this.root = root;
        this.bridge = bridge;
        this.config = config.normalize();
        this.state = state.normalize();
        this.content = content;
        this.saves = new PlayerSaveStore(root == null ? null : root.resolve("players"));
        this.progression = new ProgressionService(this.config, saves, this.state.sharedSlot);
        this.progression.onSharedDirty(() -> stateDirty = true);
        this.handService = new HandService(hands, progression);
        this.doors = new DoorService(this.state.doors);
        this.doors.setListener((door, open) -> {
            bridge.doorChanged(door, open);
            bridge.playSound(null, open ? door.openSound : door.closeSound, door.anchor, 1f);
            stateDirty = true;
        });
        this.graph = new ConnectionGraph(this.state.deviceStates);
        this.signals = new SignalBus(graph, this);
        this.power = new PowerNetwork(graph);
        this.puzzles = new PuzzleEngine(templates, this.state.puzzles, this);
        this.director = new MonsterDirector(this);
        this.chases = new ChaseController(this, this.state.completedChases);
        this.actions = new ActionRunner(this);
        this.story = new StoryEngine(this.state.firedEvents, this::facts,
                (p, id) -> progression.progress(p).map(pr -> pr.triggeredEvents.contains(id)).orElse(false),
                (p, id) -> {
                    progression.progressOrCreate(p).triggeredEvents.add(id);
                    progression.markDirty(p);
                },
                actions::run, () -> tick);
        this.dialogues = new DialogueSession(
                id -> this.content.<DialogueTree>get(ContentKind.DIALOGUES, id).orElse(null),
                this::facts, actions::run);
        this.grabPack = new GrabPackService(this);
        this.sessions = new PlayerSessions(this);
        this.interactions = new DeviceInteractions(this);
    }

    // =================================================================== creation & persistence

    /**
     * Opens (or initialises) the engine for a world. {@code worldPlaytimeDir} is
     * {@code <world save>/playtime}; it is created with default files on first load.
     */
    public static PlaytimeEngine open(Path worldPlaytimeDir, GameBridge bridge) {
        IssueList issues = new IssueList();
        WorldConfig config = loadConfig(worldPlaytimeDir.resolve("world_config.json"), issues);
        WorldState state = loadState(worldPlaytimeDir.resolve("world_state.json"), issues);
        ContentPack content = ContentLoader.load(worldPlaytimeDir.resolve("content"), true, issues);
        PlaytimeEngine engine = new PlaytimeEngine(worldPlaytimeDir, bridge, config, state, content,
                PuzzleTemplates.withBuiltins());
        engine.loadIssues.addAll(issues);
        engine.rebuild();
        engine.ensureDefaultFiles();
        return engine;
    }

    /** Engine without files (tests, previews). */
    public static PlaytimeEngine inMemory(GameBridge bridge, WorldConfig config, ContentPack content) {
        PlaytimeEngine engine = new PlaytimeEngine(null, bridge, config == null ? new WorldConfig() : config,
                new WorldState(), content == null ? new ContentPack() : content, PuzzleTemplates.withBuiltins());
        engine.rebuild();
        return engine;
    }

    private static WorldConfig loadConfig(Path file, IssueList issues) {
        try {
            String text = AtomicFiles.readStringOrBackup(file);
            if (text == null) return new WorldConfig();
            JsonElement tree = JsonParser.parseString(text);
            SaveMigrator.Result r = new SaveMigrator("world config", WorldConfig.SCHEMA_VERSION).migrate(tree.getAsJsonObject());
            if (r.warning() != null) issues.warning(file.toString(), r.warning());
            WorldConfig c = Json.fromJson(r.json(), WorldConfig.class);
            return c == null ? new WorldConfig() : c.normalize();
        } catch (IOException | RuntimeException e) {
            issues.error(file.toString(), "could not read world config, using defaults: " + e.getMessage());
            return new WorldConfig();
        }
    }

    private static WorldState loadState(Path file, IssueList issues) {
        try {
            String text = AtomicFiles.readStringOrBackup(file);
            if (text == null) return new WorldState();
            JsonElement tree = JsonParser.parseString(text);
            SaveMigrator.Result r = new SaveMigrator("world state", WorldState.SCHEMA_VERSION).migrate(tree.getAsJsonObject());
            if (r.warning() != null) issues.warning(file.toString(), r.warning());
            WorldState s = Json.fromJson(r.json(), WorldState.class);
            return s == null ? new WorldState() : s.normalize();
        } catch (IOException | RuntimeException e) {
            issues.error(file.toString(), "could not read world state, starting fresh: " + e.getMessage());
            try {
                if (Files.exists(file)) Files.move(file, file.resolveSibling("world_state.json.corrupt-" + System.currentTimeMillis()));
            } catch (IOException ignored) {
                // best effort
            }
            return new WorldState();
        }
    }

    private void ensureDefaultFiles() {
        if (root == null) return;
        try {
            Files.createDirectories(root.resolve("content"));
            Files.createDirectories(root.resolve("players"));
            if (!Files.exists(root.resolve("world_config.json"))) saveConfig();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not create Playtime folders in " + root, e);
        }
    }

    /** (Re)registers every definition from {@link #content} and restores saved world runtime state. */
    public IssueList rebuild() {
        IssueList issues = new IssueList();
        hands.clear();
        for (HandDefinition h : content.<HandDefinition>all(ContentKind.HANDS)) hands.register(h);
        credentials.clear();
        for (CredentialDefinition c : content.<CredentialDefinition>all(ContentKind.CREDENTIALS)) credentials.put(c.id, c);
        for (DoorDefinition d : content.<DoorDefinition>all(ContentKind.DOORS)) doors.register(d);
        for (RouteDefinition r : content.<RouteDefinition>all(ContentKind.ROUTES)) monsters.registerRoute(r);
        for (MonsterDefinition m : content.<MonsterDefinition>all(ContentKind.MONSTERS)) monsters.register(m);
        for (ChaseDefinition c : content.<ChaseDefinition>all(ContentKind.CHASES)) chases.register(c);
        for (StoryEvent ev : content.<StoryEvent>all(ContentKind.EVENTS)) story.register(ev);
        for (PlayerProfile p : content.<PlayerProfile>all(ContentKind.PROFILES)) profiles.register(p);
        profiles.assignments().clear();
        for (ProfileAssignment a : content.<ProfileAssignment>all(ContentKind.PROFILE_ASSIGNMENTS)) profiles.addAssignment(a);

        // Devices, links and timers are world runtime state, restored once from the save before
        // puzzles index them. Later rebuilds (content reloads) keep the live graph untouched.
        if (!runtimeRestored) {
            runtimeRestored = true;
            for (Device d : state.devices.values()) if (d != null && d.id != null && d.type != null) graph.addDevice(d);
            for (Connection c : state.connections) graph.restore(c);
            signals.restorePending(state.scheduledSignals);
            actions.restore(state.scheduledActions);
        }
        for (PuzzleDefinition p : content.<PuzzleDefinition>all(ContentKind.PUZZLES)) issues.addAll(puzzles.register(p));
        power.markDirty();
        loadIssues.addAll(issues);
        return issues;
    }

    public void saveConfig() {
        if (root == null) return;
        try {
            AtomicFiles.writeString(root.resolve("world_config.json"), Json.toJson(config));
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to save Playtime world config", e);
        }
    }

    /** Saves world state and every loaded player save. Called on world save and periodically. */
    public void save() {
        saves.saveAll();
        saveState();
    }

    private void saveState() {
        state.devices.clear();
        for (Device d : graph.devices()) state.devices.put(d.id, d);
        state.connections.clear();
        state.connections.addAll(graph.connections());
        state.scheduledSignals = signals.pendingSnapshot();
        state.scheduledActions = actions.pendingSnapshot();
        stateDirty = false;
        if (root == null) return;
        try {
            AtomicFiles.writeString(root.resolve("world_state.json"), Json.toJson(state));
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to save Playtime world state", e);
        }
    }

    /** Copies config, state and content into {@code backups/<timestamp>/}. Returns the folder. */
    public Path backup() throws IOException {
        if (root == null) throw new IOException("in-memory engine has no files");
        save();
        saveConfig();
        Path dest = root.resolve("backups").resolve(String.valueOf(System.currentTimeMillis()));
        Files.createDirectories(dest);
        for (String name : List.of("world_config.json", "world_state.json")) {
            Path src = root.resolve(name);
            if (Files.exists(src)) Files.copy(src, dest.resolve(name));
        }
        Path contentDir = root.resolve("content");
        if (Files.isDirectory(contentDir)) {
            try (var walk = Files.walk(contentDir)) {
                for (Path p : walk.toList()) {
                    Path target = dest.resolve("content").resolve(contentDir.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(target);
                    else Files.copy(p, target);
                }
            }
        }
        return dest;
    }

    // =================================================================== ticking

    /** Advance one server tick. Only active systems do work; idle worlds cost almost nothing. */
    public void tick(long gameTime) {
        tick = gameTime;
        signals.tick(tick);
        doors.tick(tick);
        actions.tick();
        PowerResult pr = power.update(tick);
        if (!pr.isEmpty()) onPowerChanged(pr);
        puzzles.tick();
        chases.tick();
        if (tick % 20 == 0) director.checkDespawnFlags();
        if (tick - lastAutosave >= AUTOSAVE_TICKS) {
            lastAutosave = tick;
            saves.saveDirty();
            if (stateDirty) saveState();
        }
    }

    private void onPowerChanged(PowerResult pr) {
        stateDirty = true;
        for (Id id : pr.tripped()) {
            graph.device(id).ifPresent(d -> {
                bridge.deviceChanged(d, graph.state(id));
                bridge.playSound(null, Id.of("generator.overload"), d.pos, 1f);
            });
        }
        Set<Id> changed = new LinkedHashSet<>(pr.nowPowered());
        changed.addAll(pr.nowUnpowered());
        for (Id id : changed) {
            boolean on = pr.nowPowered().contains(id);
            Device d = graph.device(id).orElse(null);
            if (d == null) continue;
            bridge.deviceChanged(d, graph.state(id));
            puzzles.dispatch(PuzzleEvent.device(PuzzleEventType.POWER_CHANGED, id, on, null));
            Id door = d.idSetting("door");
            if (door != null) {
                DoorDefinition def = doors.get(door).orElse(null);
                if (def != null) {
                    boolean conditions = dev.playtime.engine.logic.Condition.allMatch(def.access.conditions, facts(null));
                    doors.onPowerChanged(door, on, conditions, tick);
                }
            }
            if (on) trigger(TriggerType.DEVICE_SIGNAL, id, null);
        }
    }

    public long currentTick() {
        return tick;
    }

    // =================================================================== shared helpers

    public Facts facts(UUID player) {
        return new PlayerFacts(this, player);
    }

    /** Fires story events and chases listening for a trigger. */
    public void trigger(TriggerType type, Id id, UUID player) {
        story.onTrigger(type, id, player);
        chases.onTrigger(type, id, player);
    }

    boolean mayInteract(UUID player, Device device) {
        PlayerProfile profile = sessions.profileOf(player);
        return profile == null || profile.mayInteractWith(Id.of("device/" + device.type.name().toLowerCase()), device.id);
    }

    AccessRule scannerRule(Device scanner) {
        Id door = scanner.idSetting("door");
        if (door != null) {
            DoorDefinition def = doors.get(door).orElse(null);
            if (def != null && def.access.needsScan()) return def.access;
        }
        AccessRule rule = new AccessRule();
        for (String h : scanner.setting("hands", "").split(",")) {
            Id id = Id.tryParse(h.trim());
            if (id != null) rule.scanHands.add(id);
        }
        for (String t : scanner.setting("tags", "").split(",")) {
            Id id = Id.tryParse(t.trim());
            if (id != null) rule.scanTags.add(id);
        }
        rule.requiresPower = "true".equals(scanner.setting("requires_power", "false"));
        return rule;
    }

    AccessRequest accessRequest(UUID player, HandStats presented, Id device) {
        PlayerProgress p = progression.progressOrCreate(player);
        Set<Id> held = new HashSet<>(bridge.heldCredentials(player));
        held.addAll(p.keys);
        boolean powered = device == null || power.isPowered(device);
        return new AccessRequest(facts(player), held, p.unlockedHands, presented, powered);
    }

    /** Opens a door for a player after access was granted elsewhere (scanner, reader). */
    void openDoorFor(UUID player, Id door) {
        if (doors.open(door, tick)) {
            DoorDefinition def = doors.get(door).orElse(null);
            if (def != null && def.permanentUnlock && player != null) {
                progression.progressOrCreate(player).openedDoors.add(door);
                progression.markDirty(player);
            }
            trigger(TriggerType.DOOR_OPENED, door, player);
        }
    }

    // =================================================================== public API for the game layer

    public WorldConfig config() { return config; }

    public ContentPack content() { return content; }

    public IssueList loadIssues() { return loadIssues; }

    public ProgressionService progression() { return progression; }

    public HandService hands() { return handService; }

    public DoorService doors() { return doors; }

    public ConnectionGraph graph() { return graph; }

    public SignalBus signals() { return signals; }

    public PowerNetwork power() { return power; }

    public PuzzleEngine puzzles() { return puzzles; }

    public MonsterRegistry monsters() { return monsters; }

    public MonsterDirector director() { return director; }

    public ChaseController chases() { return chases; }

    public StoryEngine story() { return story; }

    public ProfileRegistry profiles() { return profiles; }

    public MenuTriggerTracker menus() { return menus; }

    public GrabPackService grabPack() { return grabPack; }

    public PlayerSessions sessions() { return sessions; }

    public DeviceInteractions interactions() { return interactions; }

    public GameBridge bridge() { return bridge; }

    public Set<Id> worldFlags() { return state.worldFlags; }

    public Path root() { return root; }

    /** Swaps in freshly loaded content; call {@link #rebuild()} afterwards. */
    public void replaceContent(ContentPack fresh) {
        this.content = fresh;
    }

    /** Replaces the world config (World Settings screen). The caller has checked permissions. */
    public void updateConfig(WorldConfig newConfig) {
        this.config = newConfig.normalize();
        progression.setConfig(this.config);
        saveConfig();
    }

    /**
     * Saves one edited definition into the world's content folder and re-registers it.
     * The caller has checked that the editor is an operator.
     */
    public IssueList saveDefinition(ContentKind kind, JsonObject json) {
        IssueList issues = new IssueList();
        Object def;
        try {
            def = Json.fromJson(json, kind.type);
        } catch (RuntimeException e) {
            issues.error(kind.folder, "invalid definition: " + e.getMessage());
            return issues;
        }
        Id id = ContentLoader.idOf(def);
        if (id == null) {
            issues.error(kind.folder, "definition needs an id");
            return issues;
        }
        content.put(kind, id, def, "content/" + kind.folder + "/" + id.path() + ".json");
        if (root != null) {
            try {
                ContentLoader.write(root.resolve("content"), kind, def);
            } catch (IOException e) {
                issues.error(kind.folder, "could not save: " + e.getMessage());
            }
        }
        issues.addAll(rebuild());
        return issues;
    }

    // ---- delegations used across helpers

    public void useDevice(UUID player, Id device, Id heldItem) {
        interactions.use(player, device, heldItem);
    }

    public void startDialogue(UUID player, Id dialogue) {
        DialogueSession.View view = dialogues.start(player, dialogue);
        if (view != null) bridge.openDialogue(player, view);
    }

    /** Client chose a dialogue option (index into the options it was shown). */
    public void chooseDialogueOption(UUID player, int option) {
        Id active = dialogues.activeDialogue(player);
        DialogueSession.View view = dialogues.choose(player, option);
        if (view != null) {
            bridge.openDialogue(player, view);
        } else {
            bridge.closeDialogue(player);
            if (active != null) trigger(TriggerType.DIALOGUE_FINISHED, active, player);
        }
    }

    public boolean collect(UUID player, Id collectible, boolean fromPickup) {
        return sessions.collect(player, collectible, fromPickup);
    }

    public boolean grantHand(UUID player, Id hand, boolean equip) {
        return sessions.grantHand(player, hand, equip);
    }

    public void setCheckpoint(UUID player, Id checkpoint, boolean announce) {
        sessions.setCheckpoint(player, checkpoint, announce);
    }

    public void startChapter(UUID player, Id chapter) {
        sessions.startChapter(player, chapter);
    }

    public void respawnAtCheckpoint(UUID player, Id checkpoint) {
        sessions.respawnAtCheckpoint(player, checkpoint);
    }

    public void returnToMenu(UUID player) {
        sessions.returnToMenu(player);
    }

    /** A monster caught a player: fail their chase, or send them back to their checkpoint. */
    public void onPlayerCaught(UUID player) {
        if (chases.chaseOf(player).isPresent()) {
            chases.onPlayerCaught(player);
        } else {
            sessions.respawnAtCheckpoint(player, null);
        }
    }

    // =================================================================== SignalHandler

    @Override
    public void deliver(Connection c, Device target, Signal signal, UUID player) {
        Id ref = c.targetRef != null ? c.targetRef : target.idSetting(switch (c.type) {
            case START_CHASE, STOP_CHASE -> "chase";
            case TRIGGER_EVENT -> "event";
            case RESET_PUZZLE -> "puzzle";
            default -> "door";
        });
        boolean on = signal != Signal.OFF;
        switch (c.type) {
            case OPEN_DOOR -> {
                if (ref == null) return;
                if (on) openDoorFor(player, ref); else doors.close(ref);
            }
            case CLOSE_DOOR -> {
                if (ref != null && on) doors.close(ref);
            }
            case UNLOCK_DOOR -> {
                if (ref != null && on) doors.setForcedLock(ref, false);
            }
            case LOCK_DOOR -> {
                if (ref != null && on) doors.setForcedLock(ref, true);
            }
            case START_CHASE -> {
                if (ref != null && on) chases.start(ref, player, false);
            }
            case STOP_CHASE -> {
                if (ref != null && on) chases.stop(ref);
            }
            case TRIGGER_EVENT -> {
                if (ref != null && on) story.fire(ref, player, false);
            }
            case RESET_PUZZLE -> {
                if (ref != null && on) puzzles.reset(ref, true);
            }
            case PUZZLE_INPUT -> puzzles.dispatch(new PuzzleEvent(
                    signal == Signal.PULSE ? PuzzleEventType.DEVICE_PULSED : PuzzleEventType.DEVICE_CHANGED,
                    target.id, on, null, null, Set.of(), 0, player));
            default -> { }
        }
        stateDirty = true;
    }

    @Override
    public void deviceChanged(Device device, boolean active, UUID player) {
        stateDirty = true;
        bridge.deviceChanged(device, graph.state(device.id));
        if (device.type.conductsPower()) power.markDirty();
        puzzles.dispatch(PuzzleEvent.device(PuzzleEventType.DEVICE_CHANGED, device.id, active, player));
        if (active) {
            if (player != null) {
                PlayerProgress p = progression.progressOrCreate(player);
                if (p.activatedMachinery.add(device.id)) progression.markDirty(player);
            }
            trigger(TriggerType.DEVICE_SIGNAL, device.id, player);
        }
    }

    @Override
    public void devicePulsed(Device device, UUID player) {
        puzzles.dispatch(PuzzleEvent.device(PuzzleEventType.DEVICE_PULSED, device.id, true, player));
        trigger(TriggerType.DEVICE_SIGNAL, device.id, player);
    }

    // =================================================================== PuzzleHost

    @Override
    public long tick() {
        return tick;
    }

    @Override
    public boolean isPowered(Id device) {
        return power.isPowered(device);
    }

    @Override
    public boolean isActive(Id device) {
        return graph.device(device).isPresent() && graph.state(device).active;
    }

    @Override
    public Id socket(Id device, String slot) {
        if (graph.device(device).isEmpty()) return null;
        return graph.state(device).sockets.get(slot);
    }

    @Override
    public boolean isPuzzleCompleted(Id puzzle) {
        return puzzles.isCompleted(puzzle);
    }

    @Override
    public void setDeviceActive(Id device, boolean active) {
        signals.setActive(device, active, null);
    }

    @Override
    public void run(List<Action> list, UUID player) {
        actions.run(list, player);
    }

    @Override
    public void progress(String message) {
        // Puzzle templates use the scoped context; nothing to do at engine level.
    }

    @Override
    public boolean hasItems(UUID player, Set<Id> items) {
        if (player == null) return false;
        Set<Id> keys = progression.progressOrCreate(player).keys;
        Set<Id> missing = new HashSet<>(items);
        missing.removeAll(keys);
        return missing.isEmpty() || bridge.hasItems(player, missing);
    }

    @Override
    public void onStarted(PuzzleDefinition def, PuzzleState st, UUID player) {
        stateDirty = true;
        actions.run(def.onStart, player);
    }

    @Override
    public void onCompleted(PuzzleDefinition def, PuzzleState st, UUID player) {
        stateDirty = true;
        Set<UUID> credit = new LinkedHashSet<>(st.participants);
        if (player != null) credit.add(player);
        if (def.persistent) {
            if (progression.isShared()) {
                progression.progressOrCreate(WORLD).completedPuzzles.add(def.id);
                progression.markDirty(WORLD);
            } else {
                for (UUID p : credit) {
                    progression.progressOrCreate(p).completedPuzzles.add(def.id);
                    progression.markDirty(p);
                }
            }
        }
        for (Id door : def.linkedDoors) {
            doors.setForcedLock(door, false);
            openDoorFor(player, door);
            for (UUID p : credit) progression.progressOrCreate(p).openedDoors.add(door);
        }
        if (def.completeSound != null) for (UUID p : credit) bridge.playSound(p, def.completeSound, null, 1f);
        actions.run(def.rewards, player);
        actions.run(def.onComplete, player);
        for (UUID p : credit) bridge.syncPlayer(p);
        trigger(TriggerType.PUZZLE_COMPLETED, def.id, player);
    }

    @Override
    public void onFailed(PuzzleDefinition def, PuzzleState st, boolean finalFailure) {
        stateDirty = true;
        UUID first = st.participants.stream().findFirst().orElse(null);
        if (def.failSound != null) for (UUID p : st.participants) bridge.playSound(p, def.failSound, null, 1f);
        actions.run(def.onFail, first);
    }

    @Override
    public void resetDevices(Collection<Id> deviceIds) {
        for (Id id : deviceIds) {
            Device d = graph.device(id).orElse(null);
            if (d == null) continue;
            DeviceState s = graph.state(id);
            if ("true".equals(d.setting("keep_on_reset", "false"))) continue;
            boolean was = s.active;
            s.activeInputs.clear();
            s.tempPowerUntil = 0;
            if (!"true".equals(d.setting("keep_sockets", "false"))) s.sockets.clear();
            if (was) signals.setActive(id, false, null);
            bridge.deviceChanged(d, s);
        }
        power.markDirty();
        stateDirty = true;
    }

    @Override
    public void progress(PuzzleDefinition def, Collection<UUID> players, String message) {
        for (UUID p : players) bridge.showStatus(p, message);
    }

    // =================================================================== ChaseHost

    @Override
    public UUID spawnChaseMonster(ChaseDefinition chase, ChaseStage stage, UUID target) {
        return director.spawnForChase(chase, stage, target);
    }

    @Override
    public void updateChaseMonster(UUID monster, ChaseDefinition chase, ChaseStage stage) {
        director.updateForStage(monster, chase, stage);
    }

    @Override
    public void despawnMonster(UUID monster) {
        director.despawn(monster);
    }

    @Override
    public void releaseMonster(UUID monster) {
        MonsterDirector.Instance inst = director.instance(monster);
        if (inst != null) inst.brain.endChase(tick);
    }

    @Override
    public boolean isMonsterAlive(UUID monster) {
        return director.isAlive(monster);
    }

    @Override
    public void resetPuzzles(List<Id> list) {
        for (Id id : list) puzzles.reset(id, true);
    }

    @Override
    public void markCompleted(ChaseDefinition chase, Collection<UUID> participants) {
        stateDirty = true;
        Collection<UUID> targets = progression.isShared() ? List.of(WORLD) : participants;
        for (UUID p : targets) {
            progression.progressOrCreate(p).completedChases.add(chase.id);
            progression.markDirty(p);
        }
        trigger(TriggerType.CHASE_COMPLETED, chase.id, participants.stream().findFirst().orElse(null));
    }

    @Override
    public void notify(Collection<UUID> players, String objective, int ticksLeft) {
        if (objective == null) return;
        for (UUID p : players) bridge.showObjective(p, objective, ticksLeft);
    }

    @Override
    public Collection<UUID> nearbyPlayers(UUID trigger) {
        Set<UUID> out = new LinkedHashSet<>();
        PlayerPosition origin = trigger == null ? null : bridge.position(trigger);
        double r = config.multiplayer.chaseParticipationRadius;
        for (UUID p : bridge.onlinePlayers()) {
            PlayerPosition pos = bridge.position(p);
            if (origin == null || pos == null) continue;
            if (pos.dimension().equals(origin.dimension()) && pos.vec().distanceTo(origin.vec()) <= r) out.add(p);
        }
        return out;
    }

    @Override
    public boolean failsForAll() {
        return config.multiplayer.chaseFailsForAll;
    }
}

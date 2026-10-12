package dev.playtime.engine.puzzle;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes device events to puzzles through a device index, runs the template logic and
 * applies completion, failure and reset rules. Completion is idempotent: a COMPLETED
 * puzzle ignores further events, so a shared puzzle can never complete twice.
 */
public final class PuzzleEngine {
    private static final Logger LOG = Logger.getLogger("PlaytimeEngine");

    private final PuzzleTemplates templates;
    private final Map<Id, PuzzleState> states;
    private final Map<Id, PuzzleDefinition> definitions = new LinkedHashMap<>();
    private final Map<Id, Set<Id>> deviceIndex = new HashMap<>();
    private final Set<Id> ticking = new LinkedHashSet<>();
    private final PuzzleHost host;
    /** Re-entrancy guard: completing one puzzle may trigger events for another. */
    private int depth;

    public PuzzleEngine(PuzzleTemplates templates, Map<Id, PuzzleState> savedStates, PuzzleHost host) {
        this.templates = templates;
        this.states = savedStates;
        this.host = host;
    }

    public PuzzleTemplates templates() {
        return templates;
    }

    public IssueList register(PuzzleDefinition def) {
        IssueList issues = new IssueList();
        def.normalize();
        String src = "puzzle " + def.id;
        Optional<PuzzleLogic> logic = templates.get(def.template);
        if (logic.isEmpty()) {
            issues.error(src, "unknown template " + def.template);
            return issues;
        }
        logic.get().validate(def.settings, issues, src);
        unregister(def.id);
        definitions.put(def.id, def);
        Set<Id> devices = new LinkedHashSet<>(logic.get().devices(def.settings));
        devices.addAll(def.linkedDevices);
        for (Id d : devices) deviceIndex.computeIfAbsent(d, k -> new LinkedHashSet<>()).add(def.id);
        PuzzleState state = states.computeIfAbsent(def.id, k -> new PuzzleState()).normalize();
        if (state.status == PuzzleStatus.ACTIVE && needsTick(def, logic.get())) ticking.add(def.id);
        return issues;
    }

    public void unregister(Id id) {
        if (definitions.remove(id) == null) return;
        for (Set<Id> set : deviceIndex.values()) set.remove(id);
        ticking.remove(id);
    }

    public Optional<PuzzleDefinition> definition(Id id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Collection<PuzzleDefinition> definitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public PuzzleState state(Id id) {
        return states.computeIfAbsent(id, k -> new PuzzleState()).normalize();
    }

    public boolean isCompleted(Id id) {
        PuzzleState s = states.get(id);
        return s != null && s.status == PuzzleStatus.COMPLETED;
    }

    /** Puzzles watching a device (used by the Connection Device preview and validator). */
    public Set<Id> puzzlesWatching(Id device) {
        return deviceIndex.getOrDefault(device, Set.of());
    }

    // ------------------------------------------------------------------ events

    public void dispatch(PuzzleEvent event) {
        Set<Id> watchers = deviceIndex.get(event.device());
        if (watchers == null || watchers.isEmpty()) return;
        if (depth > 8) {
            LOG.warning("Puzzle event chain too deep at " + event.device() + "; ignoring");
            return;
        }
        depth++;
        try {
            for (Id id : List.copyOf(watchers)) handle(id, event);
        } finally {
            depth--;
        }
    }

    private void handle(Id id, PuzzleEvent event) {
        PuzzleDefinition def = definitions.get(id);
        if (def == null) return;
        PuzzleState state = state(id);
        if (state.status == PuzzleStatus.COMPLETED || state.status == PuzzleStatus.FAILED) return;
        if (state.status == PuzzleStatus.INACTIVE && !activate(def, state, event.player())) return;
        if (event.player() != null) state.participants.add(event.player());
        PuzzleLogic logic = templates.get(def.template).orElse(null);
        if (logic == null) return;
        PuzzleLogic.Outcome outcome;
        try {
            outcome = logic.onEvent(run(def, state), event);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Puzzle " + id + " threw while handling " + event.type() + "; ignoring the event", e);
            return;
        }
        apply(def, state, outcome, event.player());
    }

    /** Starts a puzzle if its activation conditions hold for {@code player}. */
    public boolean activate(Id id, UUID player) {
        PuzzleDefinition def = definitions.get(id);
        if (def == null) return false;
        PuzzleState state = state(id);
        return state.status == PuzzleStatus.INACTIVE && activate(def, state, player);
    }

    private boolean activate(PuzzleDefinition def, PuzzleState state, UUID player) {
        if (!Condition.allMatch(def.activation, host.facts(player))) return false;
        state.status = PuzzleStatus.ACTIVE;
        state.startedTick = host.tick();
        state.deadlineTick = def.timeLimitTicks > 0 ? host.tick() + def.timeLimitTicks : 0;
        if (player != null) state.participants.add(player);
        PuzzleLogic logic = templates.get(def.template).orElse(null);
        if (logic == null) return false;
        if (needsTick(def, logic)) ticking.add(def.id);
        host.onStarted(def, state, player);
        try {
            logic.start(run(def, state));
            // Let templates notice requirements that were already met before activation.
            apply(def, state, logic.onEvent(run(def, state),
                    new PuzzleEvent(PuzzleEventType.INPUT, def.id, true, null, null, Set.of(), 0, player)), player);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Puzzle " + def.id + " failed to start", e);
        }
        return state.status == PuzzleStatus.ACTIVE;
    }

    private void apply(PuzzleDefinition def, PuzzleState state, PuzzleLogic.Outcome outcome, UUID player) {
        if (state.status != PuzzleStatus.ACTIVE) return;
        switch (outcome) {
            case CONTINUE -> { }
            case SUCCESS -> {
                if (!Condition.allMatch(def.success, host.facts(player))) {
                    host.progress(def, state.participants, "Something else is still needed.");
                    return;
                }
                if (!def.requiredHands.isEmpty() && !hasHands(player, def.requiredHands)) {
                    host.progress(def, state.participants, "You need a different hand for this.");
                    return;
                }
                if (!def.requiredItems.isEmpty() && !host.hasItems(player, def.requiredItems)) {
                    host.progress(def, state.participants, "You're missing something.");
                    return;
                }
                state.status = PuzzleStatus.COMPLETED;
                state.completedBy = player;
                state.deadlineTick = 0;
                ticking.remove(def.id);
                host.onCompleted(def, state, player);
                dispatch(new PuzzleEvent(PuzzleEventType.PUZZLE_COMPLETED, def.id, true, null, null, Set.of(), 0, player));
            }
            case FAILURE -> fail(def, state);
        }
    }

    private boolean hasHands(UUID player, Set<Id> hands) {
        var facts = host.facts(player);
        for (Id h : hands) if (!facts.hasHand(h)) return false;
        return true;
    }

    private void fail(PuzzleDefinition def, PuzzleState state) {
        state.attempts++;
        boolean finalFailure = def.maxAttempts > 0 && state.attempts >= def.maxAttempts;
        host.onFailed(def, state, finalFailure);
        if (!finalFailure && def.reset == ResetPolicy.ON_FAILURE) {
            restart(def, state);
        } else {
            state.status = PuzzleStatus.FAILED;
            ticking.remove(def.id);
        }
    }

    private void restart(PuzzleDefinition def, PuzzleState state) {
        state.data.clear();
        state.stage = 0;
        if (def.resetDevices) host.resetDevices(devicesOf(def));
        state.startedTick = host.tick();
        state.deadlineTick = def.timeLimitTicks > 0 ? host.tick() + def.timeLimitTicks : 0;
        templates.get(def.template).ifPresent(logic -> {
            try {
                logic.start(run(def, state));
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Puzzle " + def.id + " failed to restart", e);
            }
        });
    }

    public Set<Id> devicesOf(PuzzleDefinition def) {
        Set<Id> out = new LinkedHashSet<>(def.linkedDevices);
        templates.get(def.template).ifPresent(l -> out.addAll(l.devices(def.settings)));
        return out;
    }

    // ------------------------------------------------------------------ ticking & resets

    public void tick() {
        if (ticking.isEmpty()) return;
        for (Id id : List.copyOf(ticking)) {
            PuzzleDefinition def = definitions.get(id);
            PuzzleState state = states.get(id);
            if (def == null || state == null || state.status != PuzzleStatus.ACTIVE) {
                ticking.remove(id);
                continue;
            }
            if (state.deadlineTick > 0 && host.tick() > state.deadlineTick) {
                host.progress(def, state.participants, "Time's up.");
                fail(def, state);
                continue;
            }
            PuzzleLogic logic = templates.get(def.template).orElse(null);
            if (logic != null && logic.ticks(def.settings)) {
                try {
                    apply(def, state, logic.tick(run(def, state)), state.participants.stream().findFirst().orElse(null));
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "Puzzle " + id + " tick failed; deactivating it", e);
                    ticking.remove(id);
                }
            }
        }
    }

    /**
     * Resets a puzzle to INACTIVE (clearing devices if configured). Completed puzzles are
     * reset too, unless their policy is NEVER and {@code force} is false.
     */
    public boolean reset(Id id, boolean force) {
        PuzzleDefinition def = definitions.get(id);
        if (def == null) return false;
        PuzzleState state = state(id);
        if (!force && def.reset == ResetPolicy.NEVER && state.status == PuzzleStatus.COMPLETED) return false;
        if (def.resetDevices) host.resetDevices(devicesOf(def));
        states.put(id, new PuzzleState());
        ticking.remove(id);
        return true;
    }

    /** Resets active puzzles the dead player was working on, per their reset policies. */
    public List<Id> onPlayerDeath(UUID player, boolean worldResetsActivePuzzles) {
        List<Id> reset = new ArrayList<>();
        for (PuzzleDefinition def : definitions.values()) {
            PuzzleState s = states.get(def.id);
            if (s == null || s.status != PuzzleStatus.ACTIVE || !s.participants.contains(player)) continue;
            boolean byPolicy = def.reset == ResetPolicy.ON_DEATH
                    || worldResetsActivePuzzles && def.reset == ResetPolicy.ON_FAILURE;
            if (byPolicy && reset(def.id, false)) reset.add(def.id);
        }
        return reset;
    }

    private static boolean needsTick(PuzzleDefinition def, PuzzleLogic logic) {
        return def.timeLimitTicks > 0 || logic.ticks(def.settings);
    }

    private PuzzleRun run(PuzzleDefinition def, PuzzleState state) {
        return new PuzzleRun(def, def.settings, state, new ScopedContext(def, state), "");
    }

    /** Routes {@link PuzzleContext#progress} to this puzzle's participants. */
    private final class ScopedContext implements PuzzleContext {
        private final PuzzleDefinition def;
        private final PuzzleState state;

        ScopedContext(PuzzleDefinition def, PuzzleState state) {
            this.def = def;
            this.state = state;
        }

        @Override public long tick() { return host.tick(); }

        @Override public boolean isPowered(Id device) { return host.isPowered(device); }

        @Override public boolean isActive(Id device) { return host.isActive(device); }

        @Override public Id socket(Id device, String slot) { return host.socket(device, slot); }

        @Override public boolean isPuzzleCompleted(Id puzzle) { return isCompleted(puzzle); }

        @Override public void setDeviceActive(Id device, boolean active) { host.setDeviceActive(device, active); }

        @Override public void run(List<Action> actions, UUID player) { host.run(actions, player); }

        @Override public void progress(String message) { host.progress(def, state.participants, message); }
    }
}

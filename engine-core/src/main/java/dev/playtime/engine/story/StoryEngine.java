package dev.playtime.engine.story;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.logic.TriggerType;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Fires story events from game triggers. Events are indexed by trigger type so a trigger
 * only looks at the events that can match it.
 */
public final class StoryEngine {
    private static final Logger LOG = Logger.getLogger("PlaytimeEngine");
    private static final int MAX_DEPTH = 16;

    private final Map<Id, StoryEvent> events = new LinkedHashMap<>();
    private final Map<TriggerType, List<StoryEvent>> byTrigger = new EnumMap<>(TriggerType.class);
    private final Map<Id, Long> lastFired = new HashMap<>();
    private final Set<Id> worldFired;
    private final Function<UUID, Facts> facts;
    private final BiPredicate<UUID, Id> firedForSave;
    private final BiConsumer<UUID, Id> recordForSave;
    private final BiConsumer<List<Action>, UUID> runner;
    private final java.util.function.LongSupplier clock;
    private int depth;

    public StoryEngine(Set<Id> worldFired, Function<UUID, Facts> facts, BiPredicate<UUID, Id> firedForSave,
                       BiConsumer<UUID, Id> recordForSave, BiConsumer<List<Action>, UUID> runner,
                       java.util.function.LongSupplier clock) {
        this.worldFired = worldFired;
        this.facts = facts;
        this.firedForSave = firedForSave;
        this.recordForSave = recordForSave;
        this.runner = runner;
        this.clock = clock;
    }

    public void register(StoryEvent event) {
        event.normalize();
        StoryEvent old = events.put(event.id, event);
        if (old != null) byTrigger.getOrDefault(old.trigger.type, new ArrayList<>()).remove(old);
        byTrigger.computeIfAbsent(event.trigger.type, k -> new ArrayList<>()).add(event);
    }

    public Optional<StoryEvent> get(Id id) {
        return Optional.ofNullable(events.get(id));
    }

    public Collection<StoryEvent> all() {
        return Collections.unmodifiableCollection(events.values());
    }

    public List<StoryEvent> withTrigger(TriggerType type) {
        return byTrigger.getOrDefault(type, List.of());
    }

    public void onTrigger(TriggerType type, Id id, UUID player) {
        for (StoryEvent e : List.copyOf(withTrigger(type))) {
            if (e.trigger.matches(type, id)) fire(e.id, player, false);
        }
    }

    /**
     * Fires an event. With {@code force} the repeat rule and conditions are ignored (admin test).
     * Returns false if the event did not run.
     */
    public boolean fire(Id id, UUID player, boolean force) {
        StoryEvent e = events.get(id);
        if (e == null) return false;
        if (depth >= MAX_DEPTH) {
            LOG.warning("Story event chain too deep at " + id + "; check events that trigger each other");
            return false;
        }
        long now = clock.getAsLong();
        if (!force) {
            if (e.repeat == Repeat.ONCE_PER_WORLD && worldFired.contains(id)) return false;
            if (e.repeat == Repeat.ONCE_PER_SAVE && player != null && firedForSave.test(player, id)) return false;
            if (e.repeat == Repeat.ONCE_PER_SAVE && player == null && worldFired.contains(id)) return false;
            Long last = lastFired.get(id);
            if (e.cooldownTicks > 0 && last != null && now - last < e.cooldownTicks) return false;
            if (!Condition.allMatch(e.conditions, facts.apply(player))) return false;
        }
        lastFired.put(id, now);
        worldFired.add(id);
        if (player != null) recordForSave.accept(player, id);
        depth++;
        try {
            runner.accept(e.actions, player);
        } finally {
            depth--;
        }
        return true;
    }
}

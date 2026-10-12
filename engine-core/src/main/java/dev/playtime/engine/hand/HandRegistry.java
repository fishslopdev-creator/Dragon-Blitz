package dev.playtime.engine.hand;

import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All hand definitions known to the current world. Later registrations override earlier ones. */
public final class HandRegistry {
    private final Map<Id, HandDefinition> hands = new LinkedHashMap<>();

    public void register(HandDefinition def) {
        if (def == null || def.id == null) throw new IllegalArgumentException("Hand definition needs an id");
        hands.put(def.id, def.normalize());
    }

    public Optional<HandDefinition> get(Id id) {
        return Optional.ofNullable(id == null ? null : hands.get(id));
    }

    public boolean contains(Id id) {
        return hands.containsKey(id);
    }

    public Collection<HandDefinition> all() {
        return Collections.unmodifiableCollection(hands.values());
    }

    /** Hands in wheel order. */
    public List<HandDefinition> sorted() {
        List<HandDefinition> list = new ArrayList<>(hands.values());
        list.sort(Comparator.comparingInt((HandDefinition h) -> h.sortOrder).thenComparing(h -> h.id));
        return list;
    }

    public void clear() {
        hands.clear();
    }
}

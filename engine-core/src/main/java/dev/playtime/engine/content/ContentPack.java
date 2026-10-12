package dev.playtime.engine.content;

import dev.playtime.engine.util.Id;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** All definitions loaded for a world, keyed by kind and id. Later puts override earlier ones. */
public final class ContentPack {
    private final Map<ContentKind, Map<Id, Object>> content = new EnumMap<>(ContentKind.class);
    private final Map<ContentKind, Map<Id, String>> sources = new EnumMap<>(ContentKind.class);

    public void put(ContentKind kind, Id id, Object definition, String source) {
        content.computeIfAbsent(kind, k -> new LinkedHashMap<>()).put(id, definition);
        sources.computeIfAbsent(kind, k -> new LinkedHashMap<>()).put(id, source);
    }

    public boolean remove(ContentKind kind, Id id) {
        Map<Id, Object> map = content.get(kind);
        return map != null && map.remove(id) != null;
    }

    @SuppressWarnings("unchecked")
    public <T> Collection<T> all(ContentKind kind) {
        Map<Id, Object> map = content.get(kind);
        return map == null ? Collections.emptyList() : (Collection<T>) Collections.unmodifiableCollection(map.values());
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(ContentKind kind, Id id) {
        Map<Id, Object> map = content.get(kind);
        return Optional.ofNullable(map == null ? null : (T) map.get(id));
    }

    public boolean contains(ContentKind kind, Id id) {
        Map<Id, Object> map = content.get(kind);
        return map != null && id != null && map.containsKey(id);
    }

    public String source(ContentKind kind, Id id) {
        Map<Id, String> map = sources.get(kind);
        return map == null ? "?" : map.getOrDefault(id, "?");
    }

    public int size(ContentKind kind) {
        Map<Id, Object> map = content.get(kind);
        return map == null ? 0 : map.size();
    }
}

package dev.playtime.engine.monster;

import dev.playtime.engine.util.Id;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MonsterRegistry {
    private final Map<Id, MonsterDefinition> monsters = new LinkedHashMap<>();
    private final Map<Id, RouteDefinition> routes = new LinkedHashMap<>();

    public void register(MonsterDefinition def) {
        if (def == null || def.id == null) throw new IllegalArgumentException("Monster definition needs an id");
        monsters.put(def.id, def.normalize());
    }

    public void registerRoute(RouteDefinition route) {
        if (route == null || route.id == null) throw new IllegalArgumentException("Route needs an id");
        if (route.points == null) route.points = new java.util.ArrayList<>();
        routes.put(route.id, route);
    }

    public Optional<MonsterDefinition> get(Id id) {
        return Optional.ofNullable(id == null ? null : monsters.get(id));
    }

    public Optional<RouteDefinition> route(Id id) {
        return Optional.ofNullable(id == null ? null : routes.get(id));
    }

    public Collection<MonsterDefinition> all() {
        return Collections.unmodifiableCollection(monsters.values());
    }

    public Collection<RouteDefinition> routes() {
        return Collections.unmodifiableCollection(routes.values());
    }
}

package dev.playtime.engine.profile;

import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Named profiles plus automatic assignment rules. */
public final class ProfileRegistry {
    private final Map<Id, PlayerProfile> profiles = new LinkedHashMap<>();
    private final List<ProfileAssignment> assignments = new ArrayList<>();

    public void register(PlayerProfile profile) {
        profiles.put(profile.id, profile.normalize());
    }

    public boolean remove(Id id) {
        return profiles.remove(id) != null;
    }

    public Optional<PlayerProfile> get(Id id) {
        return Optional.ofNullable(id == null ? null : profiles.get(id));
    }

    public Collection<PlayerProfile> all() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    public List<ProfileAssignment> assignments() {
        return assignments;
    }

    public void addAssignment(ProfileAssignment a) {
        assignments.add(a);
    }

    /**
     * Picks the profile for a player: an explicit assignment from their save wins, then
     * the highest-priority matching rule, then the world default.
     */
    public Optional<PlayerProfile> resolve(PlayerContext ctx, Id explicit, Id worldDefault) {
        if (explicit != null && profiles.containsKey(explicit)) return get(explicit);
        ProfileAssignment best = null;
        for (ProfileAssignment a : assignments) {
            if (a == null || a.profile == null || !profiles.containsKey(a.profile)) continue;
            if (!matches(a, ctx)) continue;
            if (best == null || a.priority > best.priority) best = a;
        }
        if (best != null) return get(best.profile);
        return get(worldDefault);
    }

    static boolean matches(ProfileAssignment a, PlayerContext ctx) {
        return switch (a.type) {
            case PLAYER -> a.match != null && (a.match.equalsIgnoreCase(ctx.name())
                    || a.match.equalsIgnoreCase(String.valueOf(ctx.uuid())));
            case TEAM -> a.match != null && a.match.equals(ctx.team());
            case AREA -> a.area != null && a.area.contains(ctx.dimension(), ctx.x(), ctx.y(), ctx.z());
            case SCENARIO -> {
                Id flag = Id.tryParse(a.match);
                yield flag != null && ctx.worldFlags() != null && ctx.worldFlags().contains(flag);
            }
        };
    }
}

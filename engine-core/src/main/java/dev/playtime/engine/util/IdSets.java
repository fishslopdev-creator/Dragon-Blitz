package dev.playtime.engine.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Null-tolerant helpers for the identifier sets that appear throughout map JSON. */
public final class IdSets {
    private IdSets() {}

    public static Set<Id> copy(Collection<Id> source) {
        Set<Id> out = new LinkedHashSet<>();
        if (source != null) {
            for (Id id : source) {
                if (id != null) out.add(id);
            }
        }
        return out;
    }

    public static boolean containsAll(Collection<Id> have, Collection<Id> need) {
        if (need == null || need.isEmpty()) return true;
        if (have == null) return false;
        return have.containsAll(need);
    }

    public static boolean containsAny(Collection<Id> have, Collection<Id> any) {
        if (any == null || any.isEmpty()) return true;
        if (have == null) return false;
        for (Id id : any) {
            if (have.contains(id)) return true;
        }
        return false;
    }
}

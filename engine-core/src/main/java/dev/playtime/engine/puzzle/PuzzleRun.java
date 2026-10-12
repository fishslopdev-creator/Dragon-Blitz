package dev.playtime.engine.puzzle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A template's view of one running puzzle: its settings, saved scratch data and the
 * engine context. {@code prefix} namespaces data keys so staged puzzles can nest templates.
 */
public final class PuzzleRun {
    private final PuzzleDefinition definition;
    private final JsonObject settings;
    private final PuzzleState state;
    private final PuzzleContext context;
    private final String prefix;

    public PuzzleRun(PuzzleDefinition definition, JsonObject settings, PuzzleState state, PuzzleContext context, String prefix) {
        this.definition = definition;
        this.settings = settings == null ? new JsonObject() : settings;
        this.state = state;
        this.context = context;
        this.prefix = prefix == null ? "" : prefix;
    }

    public PuzzleDefinition definition() { return definition; }

    public JsonObject settings() { return settings; }

    public PuzzleState state() { return state; }

    public PuzzleContext ctx() { return context; }

    public PuzzleRun nested(JsonObject subSettings, String subPrefix) {
        return new PuzzleRun(definition, subSettings, state, context, prefix + subPrefix);
    }

    // ---------------------------------------------------------------- scratch data

    public String get(String key) { return state.data.get(prefix + key); }

    public void put(String key, String value) {
        if (value == null) state.data.remove(prefix + key); else state.data.put(prefix + key, value);
    }

    public int getInt(String key, int fallback) {
        try {
            String v = get(key);
            return v == null ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void putInt(String key, int value) { put(key, Integer.toString(value)); }

    public long getLong(String key, long fallback) {
        try {
            String v = get(key);
            return v == null ? fallback : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void putLong(String key, long value) { put(key, Long.toString(value)); }

    public List<Id> getIds(String key) {
        List<Id> out = new ArrayList<>();
        String v = get(key);
        if (v == null || v.isEmpty()) return out;
        for (String s : v.split(",")) {
            Id id = Id.tryParse(s);
            if (id != null) out.add(id);
        }
        return out;
    }

    public void putIds(String key, List<Id> ids) {
        List<String> parts = new ArrayList<>();
        for (Id id : ids) parts.add(id.toString());
        put(key, String.join(",", parts));
    }

    /** Removes every data key under this run's prefix. */
    public void clearData() {
        state.data.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // ---------------------------------------------------------------- settings helpers

    public static Id id(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? Id.tryParse(e.getAsString()) : null;
    }

    public static List<Id> ids(JsonObject o, String key) {
        List<Id> out = new ArrayList<>();
        JsonElement e = o == null ? null : o.get(key);
        if (e instanceof JsonArray arr) {
            for (JsonElement x : arr) {
                Id id = x.isJsonPrimitive() ? Id.tryParse(x.getAsString()) : null;
                if (id != null) out.add(id);
            }
        }
        return out;
    }

    public static Set<Id> idSet(JsonObject o, String key) {
        return new LinkedHashSet<>(ids(o, key));
    }

    public static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o == null ? null : o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
    }

    /** Reads {@code {"slot": "item"}} maps. */
    public static Map<String, Id> idMap(JsonObject o, String key) {
        Map<String, Id> out = new LinkedHashMap<>();
        JsonElement e = o == null ? null : o.get(key);
        if (e instanceof JsonObject obj) {
            for (Map.Entry<String, JsonElement> en : obj.entrySet()) {
                Id id = en.getValue().isJsonPrimitive() ? Id.tryParse(en.getValue().getAsString()) : null;
                if (id != null) out.put(en.getKey(), id);
            }
        }
        return out;
    }
}

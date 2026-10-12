package dev.playtime.engine.progress;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Upgrades saved JSON from older schema versions. Each step converts version {@code n}
 * to {@code n + 1}. Files newer than the running engine are loaded as-is with a warning
 * by the caller rather than being rewritten destructively.
 */
public final class SaveMigrator {
    private final String kind;
    private final int currentVersion;
    private final List<UnaryOperator<JsonObject>> steps = new ArrayList<>();

    public SaveMigrator(String kind, int currentVersion) {
        this.kind = kind;
        this.currentVersion = currentVersion;
    }

    /** Registers the step that upgrades {@code fromVersion} to {@code fromVersion + 1}. */
    public SaveMigrator step(int fromVersion, UnaryOperator<JsonObject> step) {
        while (steps.size() <= fromVersion) steps.add(null);
        steps.set(fromVersion, step);
        return this;
    }

    public Result migrate(JsonObject json) {
        int version = json.has("schemaVersion") ? json.get("schemaVersion").getAsInt() : 0;
        if (version > currentVersion) {
            return new Result(json, version, false,
                    kind + " was saved by a newer engine (schema " + version + " > " + currentVersion + ")");
        }
        boolean changed = false;
        JsonObject current = json;
        while (version < currentVersion) {
            UnaryOperator<JsonObject> step = version < steps.size() ? steps.get(version) : null;
            if (step != null) current = step.apply(current);
            version++;
            current.addProperty("schemaVersion", version);
            changed = true;
        }
        return new Result(current, version, changed, null);
    }

    public record Result(JsonObject json, int version, boolean changed, String warning) {}
}

package dev.playtime.engine.sample;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.playtime.engine.config.WorldConfig;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;
import dev.playtime.engine.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Installs the sample game (Pulse Hand pickup, The Assembly Line puzzle, The Playwright
 * and the Factory Shutdown chase) at a chosen origin.
 *
 * <p>The sample is ordinary map content: JSON files under {@code playtime/sample/} whose
 * positions are written relative to the origin ({@code "rel": [dx, dy, dz]}). Installing
 * converts them to absolute positions and saves them into the world's content folder,
 * exactly as the in-game editors would, so map makers can open and study the files.
 */
public final class SampleScenario {
    public static final String ROOT = "/playtime/sample/";

    private SampleScenario() {}

    public record Result(List<Device> devices, IssueList issues) {}

    public static Result install(PlaytimeEngine engine, String dimension, int ox, int oy, int oz, boolean configureWorld) {
        IssueList issues = new IssueList();
        Transform t = new Transform(dimension, ox, oy, oz);

        // 1. Content definitions (doors, puzzle, chase, events...).
        JsonArray index = read("index.json").getAsJsonArray();
        for (JsonElement entry : index) {
            String rel = entry.getAsString();
            ContentKind kind = ContentKind.byFolder(rel.substring(0, rel.indexOf('/')));
            if (kind == null) {
                issues.warning("sample/" + rel, "unknown content folder");
                continue;
            }
            JsonObject json = read("content/" + rel).getAsJsonObject();
            t.apply(json);
            issues.addAll(engine.saveDefinition(kind, json));
        }

        // 2. Engine blocks.
        List<Device> placed = new ArrayList<>();
        for (JsonElement e : read("devices.json").getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            DeviceType type = DeviceType.valueOf(o.get("type").getAsString().toUpperCase(Locale.ROOT));
            JsonArray r = o.getAsJsonArray("rel");
            BlockRef pos = new BlockRef(dimension, ox + r.get(0).getAsInt(), oy + r.get(1).getAsInt(), oz + r.get(2).getAsInt());
            Map<String, String> settings = new LinkedHashMap<>();
            if (o.has("settings")) {
                for (var s : o.getAsJsonObject("settings").entrySet()) settings.put(s.getKey(), s.getValue().getAsString());
            }
            Id id = engine.interactions().place(type, pos, Id.parse(o.get("id").getAsString()), settings);
            Device d = engine.graph().device(id).orElseThrow();
            d.name = o.has("name") ? o.get("name").getAsString() : null;
            d.inputsRequired = o.has("inputsRequired") ? o.get("inputsRequired").getAsInt() : 1;
            d.latch = o.has("latch") && o.get("latch").getAsBoolean();
            placed.add(d);
        }

        // 3. Links, exactly as the Connection Device creates them.
        for (JsonElement e : read("connections.json").getAsJsonArray()) {
            Connection c = Json.fromJson(e, Connection.class);
            issues.addAll(engine.interactions().connect(c));
        }

        // 4. Re-index puzzles now that their devices exist.
        issues.addAll(engine.rebuild());

        if (configureWorld) {
            WorldConfig cfg = engine.config();
            cfg.gameTitle = "Playtime Engine";
            cfg.subtitle = "Sample: The Assembly Floor";
            cfg.chapterName = "The Assembly Floor";
            cfg.startingChapter = Id.parse("playtime:sample/factory");
            cfg.defaultProfile = Id.parse("playtime:sample/visitor");
            engine.updateConfig(cfg);
        }
        engine.save();
        return new Result(placed, issues);
    }

    private static JsonElement read(String rel) {
        try (InputStream in = SampleScenario.class.getResourceAsStream(ROOT + rel)) {
            if (in == null) throw new IllegalStateException("missing sample resource " + rel);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read sample resource " + rel, e);
        }
    }

    /** Rewrites {@code rel*} keys: 3 numbers become a block position, 6 numbers a box. */
    static final class Transform {
        private final String dimension;
        private final int ox, oy, oz;

        Transform(String dimension, int ox, int oy, int oz) {
            this.dimension = dimension;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }

        void apply(JsonElement element) {
            if (element instanceof JsonArray arr) {
                for (JsonElement e : arr) apply(e);
            } else if (element instanceof JsonObject obj) {
                for (String key : List.copyOf(obj.keySet())) {
                    JsonElement v = obj.get(key);
                    if (key.startsWith("rel") && v instanceof JsonArray arr && (arr.size() == 3 || arr.size() == 6)) {
                        obj.remove(key);
                        String target = key.equals("rel") ? "pos" : Character.toLowerCase(key.charAt(3)) + key.substring(4);
                        obj.add(target, arr.size() == 3 ? pos(arr) : box(arr));
                    } else {
                        apply(v);
                    }
                }
            }
        }

        private JsonObject pos(JsonArray a) {
            JsonObject o = new JsonObject();
            o.addProperty("dimension", dimension);
            o.addProperty("x", ox + a.get(0).getAsInt());
            o.addProperty("y", oy + a.get(1).getAsInt());
            o.addProperty("z", oz + a.get(2).getAsInt());
            return o;
        }

        private JsonObject box(JsonArray a) {
            JsonObject o = new JsonObject();
            o.addProperty("dimension", dimension);
            o.addProperty("minX", ox + a.get(0).getAsInt());
            o.addProperty("minY", oy + a.get(1).getAsInt());
            o.addProperty("minZ", oz + a.get(2).getAsInt());
            o.addProperty("maxX", ox + a.get(3).getAsInt());
            o.addProperty("maxY", oy + a.get(4).getAsInt());
            o.addProperty("maxZ", oz + a.get(5).getAsInt());
            return o;
        }
    }
}

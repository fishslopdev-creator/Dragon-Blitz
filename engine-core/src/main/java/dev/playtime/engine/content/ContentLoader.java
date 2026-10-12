package dev.playtime.engine.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.playtime.engine.util.AtomicFiles;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;
import dev.playtime.engine.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads built-in content from the engine jar, then the world's own content folder on top.
 * Every file is parsed independently; a malformed file becomes an error in the report and
 * the rest of the map still loads.
 */
public final class ContentLoader {
    public static final String BUILTIN_ROOT = "/playtime/builtin/";
    public static final String BUILTIN_INDEX = BUILTIN_ROOT + "index.json";

    private ContentLoader() {}

    public static ContentPack load(Path worldContentDir, boolean includeBuiltins, IssueList issues) {
        ContentPack pack = new ContentPack();
        if (includeBuiltins) loadBuiltins(pack, issues);
        if (worldContentDir != null && Files.isDirectory(worldContentDir)) loadDirectory(pack, worldContentDir, issues);
        return pack;
    }

    public static void loadBuiltins(ContentPack pack, IssueList issues) {
        try (InputStream in = ContentLoader.class.getResourceAsStream(BUILTIN_INDEX)) {
            if (in == null) {
                issues.warning("builtin", "built-in content index is missing");
                return;
            }
            JsonElement index = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            for (JsonElement entry : index.getAsJsonArray()) {
                String rel = entry.getAsString();
                try (InputStream file = ContentLoader.class.getResourceAsStream(BUILTIN_ROOT + rel)) {
                    if (file == null) {
                        issues.error("builtin/" + rel, "listed in index but missing");
                        continue;
                    }
                    String text = new String(file.readAllBytes(), StandardCharsets.UTF_8);
                    loadOne(pack, rel, text, "builtin/" + rel, issues);
                }
            }
        } catch (IOException | RuntimeException e) {
            issues.error("builtin", "could not read built-in content: " + e.getMessage());
        }
    }

    public static void loadDirectory(ContentPack pack, Path root, IssueList issues) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".json") && Files.isRegularFile(p)).sorted().forEach(files::add);
        } catch (IOException e) {
            issues.error(root.toString(), "could not list content: " + e.getMessage());
            return;
        }
        for (Path file : files) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            try {
                loadOne(pack, rel, Files.readString(file, StandardCharsets.UTF_8), rel, issues);
            } catch (IOException e) {
                issues.error(rel, "could not read: " + e.getMessage());
            }
        }
    }

    /** @param rel path like {@code hands/blue_hand.json}; the first folder selects the kind */
    static void loadOne(ContentPack pack, String rel, String text, String source, IssueList issues) {
        int slash = rel.indexOf('/');
        if (slash < 0) {
            issues.warning(source, "ignored: content files must be inside a kind folder such as hands/ or doors/");
            return;
        }
        ContentKind kind = ContentKind.byFolder(rel.substring(0, slash));
        if (kind == null) {
            issues.warning(source, "ignored: unknown content folder '" + rel.substring(0, slash) + "'");
            return;
        }
        try {
            JsonElement tree = JsonParser.parseString(text);
            if (!tree.isJsonObject()) {
                issues.error(source, "expected a JSON object");
                return;
            }
            JsonObject obj = tree.getAsJsonObject();
            if (!obj.has("id")) {
                String path = rel.substring(slash + 1, rel.length() - ".json".length());
                obj.addProperty("id", Id.DEFAULT_NAMESPACE + ":" + path);
            }
            Object def = Json.fromJson(obj, kind.type);
            Id id = idOf(def);
            if (id == null) {
                issues.error(source, "definition has no valid id");
                return;
            }
            pack.put(kind, id, def, source);
        } catch (RuntimeException e) {
            issues.error(source, "invalid JSON: " + e.getMessage());
        }
    }

    public static Id idOf(Object definition) {
        try {
            Field f = definition.getClass().getField("id");
            Object v = f.get(definition);
            return v instanceof Id id ? id : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Writes one definition as {@code <root>/<kind>/<path>.json} (used by the in-game editors). */
    public static Path write(Path root, ContentKind kind, Object definition) throws IOException {
        Id id = idOf(definition);
        if (id == null) throw new IOException("definition has no id");
        String name = (id.namespace().equals(Id.DEFAULT_NAMESPACE) ? "" : id.namespace() + "/") + id.path();
        Path file = root.resolve(kind.folder).resolve(name + ".json").normalize();
        if (!file.startsWith(root.normalize())) throw new IOException("refusing to write outside the content folder");
        AtomicFiles.writeString(file, Json.toJson(definition));
        return file;
    }

    public static boolean delete(Path root, ContentKind kind, Id id) throws IOException {
        String name = (id.namespace().equals(Id.DEFAULT_NAMESPACE) ? "" : id.namespace() + "/") + id.path();
        Path file = root.resolve(kind.folder).resolve(name + ".json").normalize();
        if (!file.startsWith(root.normalize())) return false;
        return Files.deleteIfExists(file);
    }
}

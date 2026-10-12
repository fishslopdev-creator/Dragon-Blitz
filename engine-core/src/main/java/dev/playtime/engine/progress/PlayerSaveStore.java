package dev.playtime.engine.progress;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.playtime.engine.util.AtomicFiles;
import dev.playtime.engine.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and saves per-player files lazily. A corrupt file is moved aside and replaced
 * with a fresh save rather than crashing the server.
 */
public final class PlayerSaveStore {
    private static final Logger LOG = Logger.getLogger("PlaytimeEngine");

    private final Path directory;
    private final SaveMigrator migrator = new SaveMigrator("player save", PlayerSave.SCHEMA_VERSION);
    private final Map<UUID, PlayerSave> loaded = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    /** @param directory {@code <world>/playtime/players}, or {@code null} for an in-memory store (tests) */
    public PlayerSaveStore(Path directory) {
        this.directory = directory;
    }

    public PlayerSave get(UUID player) {
        return loaded.computeIfAbsent(player, this::load);
    }

    public void markDirty(UUID player) {
        dirty.add(player);
    }

    public Collection<PlayerSave> loadedSaves() {
        return loaded.values();
    }

    /** Lists every player that has a save on disk or in memory. */
    public Set<UUID> knownPlayers() {
        Set<UUID> out = new HashSet<>(loaded.keySet());
        if (directory != null && Files.isDirectory(directory)) {
            try (var stream = Files.list(directory)) {
                stream.forEach(p -> {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".json")) {
                        try {
                            out.add(UUID.fromString(name.substring(0, name.length() - 5)));
                        } catch (IllegalArgumentException ignored) {
                            // Not a player save.
                        }
                    }
                });
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not list player saves in " + directory, e);
            }
        }
        return out;
    }

    public void saveDirty() {
        for (UUID id : Set.copyOf(dirty)) {
            if (save(id)) dirty.remove(id);
        }
    }

    public void saveAll() {
        for (UUID id : Set.copyOf(loaded.keySet())) save(id);
        dirty.clear();
    }

    /** Saves and forgets a player (on disconnect). */
    public void unload(UUID player) {
        if (loaded.containsKey(player)) save(player);
        loaded.remove(player);
        dirty.remove(player);
    }

    /** Deletes a player's save entirely (admin reset of a test save). */
    public void delete(UUID player) {
        loaded.remove(player);
        dirty.remove(player);
        if (directory != null) {
            try {
                Files.deleteIfExists(file(player));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not delete save for " + player, e);
            }
        }
    }

    private boolean save(UUID id) {
        PlayerSave save = loaded.get(id);
        if (save == null || directory == null) return true;
        try {
            AtomicFiles.writeString(file(id), Json.toJson(save));
            return true;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to save Playtime progress for " + id, e);
            return false;
        }
    }

    private PlayerSave load(UUID id) {
        PlayerSave fresh = new PlayerSave();
        fresh.player = id;
        if (directory == null) return fresh;
        Path file = file(id);
        try {
            String text = AtomicFiles.readStringOrBackup(file);
            if (text == null) return fresh;
            JsonElement tree = JsonParser.parseString(text);
            if (!tree.isJsonObject()) throw new JsonParseException("save is not a JSON object");
            SaveMigrator.Result result = migrator.migrate(tree.getAsJsonObject());
            if (result.warning() != null) LOG.warning(result.warning() + " (" + file + ")");
            PlayerSave save = Json.fromJson(result.json(), PlayerSave.class);
            if (save == null) return fresh;
            save.player = id;
            if (save.slots == null) save.slots = new java.util.ArrayList<>();
            for (SaveSlot slot : save.slots) {
                if (slot != null && slot.progress != null) slot.progress.normalize();
            }
            save.slots.replaceAll(s -> s == null ? new SaveSlot() : s);
            if (result.changed()) dirty.add(id);
            return save;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Corrupt Playtime save for " + id + "; moving it aside and starting fresh", e);
            try {
                if (Files.exists(file)) {
                    Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
                }
            } catch (IOException moveError) {
                LOG.log(Level.WARNING, "Could not move corrupt save aside", moveError);
            }
            return fresh;
        }
    }

    private Path file(UUID id) {
        return directory.resolve(id + ".json");
    }

    /** Exposed for tests and the backup command. */
    public static JsonObject toJson(PlayerSave save) {
        return Json.toTree(save).getAsJsonObject();
    }
}

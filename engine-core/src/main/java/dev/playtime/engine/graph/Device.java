package dev.playtime.engine.graph;

import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A placed engine device. Devices are registered when their block is placed and saved
 * in world state, so lookups never need to scan the world.
 */
public class Device {
    public Id id;
    public DeviceType type;
    public String name;
    public BlockRef pos;
    /** Number of distinct active inputs needed to activate (an AND of several power nodes, switches...). */
    public int inputsRequired = 1;
    /** Once active, stay active even if inputs drop (used for "permanently unlocks" behaviour). */
    public boolean latch;
    /** Type-specific settings edited in the device GUI (capacity, threshold, linked door id...). */
    public Map<String, String> settings = new LinkedHashMap<>();

    public Device() {}

    public Device(Id id, DeviceType type, BlockRef pos) {
        this.id = id;
        this.type = type;
        this.pos = pos;
    }

    public String setting(String key, String fallback) {
        String v = settings == null ? null : settings.get(key);
        return v == null ? fallback : v;
    }

    public int intSetting(String key, int fallback) {
        try {
            String v = setting(key, null);
            return v == null ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public Id idSetting(String key) {
        return Id.tryParse(setting(key, null));
    }

    public String displayName() {
        return name != null && !name.isBlank() ? name : id.toString();
    }
}

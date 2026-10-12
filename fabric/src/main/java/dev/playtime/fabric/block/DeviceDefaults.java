package dev.playtime.fabric.block;

import dev.playtime.engine.graph.DeviceType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sensible starting settings for newly placed devices; editable in the device editor. */
final class DeviceDefaults {
    private DeviceDefaults() {}

    static Map<String, String> of(DeviceType type) {
        Map<String, String> m = new LinkedHashMap<>();
        switch (type) {
            case GENERATOR -> m.put("capacity", "10");
            case BATTERY_DEVICE, CHARGING_STATION -> m.put("capacity", "4");
            case TIMED_SWITCH -> m.put("duration", "100");
            case LAUNCH_PAD -> {
                m.put("hand_tag", "playtime:purple_pad");
                m.put("strength", "12");
            }
            case HAND_SCANNER -> m.put("hands", "playtime:green_hand");
            case KEYCARD_READER -> m.put("credentials", "playtime:blue_access_card");
            default -> { }
        }
        return m;
    }
}

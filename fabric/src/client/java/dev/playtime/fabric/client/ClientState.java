package dev.playtime.fabric.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Last data received from the server, read by the HUD, wheel and journal. */
public final class ClientState {
    public static JsonObject sync = new JsonObject();
    public static String objectiveText = "";
    public static int objectiveTicks;
    public static long objectiveReceivedAt;
    public static String unlockTitle = "";
    public static String unlockDescription = "";
    public static long unlockUntil;
    public static int fadeTicks;
    public static int fadeTotal;
    public static int shakeTicks;
    public static float shakeStrength;
    /** Cables per player (hand position in world space). */
    public static final Map<UUID, List<Cable>> CABLES = new HashMap<>();

    public record Cable(String side, String hand, double x, double y, double z, String state) {}

    private ClientState() {}

    public static void reset() {
        sync = new JsonObject();
        objectiveText = "";
        objectiveTicks = 0;
        unlockTitle = "";
        CABLES.clear();
    }

    public static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    public static List<JsonObject> array(JsonObject o, String key) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement e = o == null ? null : o.get(key);
        if (e instanceof JsonArray arr) for (JsonElement x : arr) if (x.isJsonObject()) out.add(x.getAsJsonObject());
        return out;
    }

    /** ARGB colour for a built-in hand id, used for cables and wheel highlights. */
    public static int handColor(String hand) {
        for (JsonObject w : array(sync, "rightWheel")) if (hand.equals(str(w, "hand"))) return w.get("color").getAsInt();
        for (JsonObject w : array(sync, "leftWheel")) if (hand.equals(str(w, "hand"))) return w.get("color").getAsInt();
        return 0xFFB0B0B0;
    }
}

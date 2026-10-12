package dev.playtime.fabric.client;

import com.google.gson.JsonObject;
import dev.playtime.fabric.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Small helpers for sending requests to the server. */
public final class ClientSend {
    private ClientSend() {}

    public static void send(String kind, JsonObject body) {
        if (ClientPlayNetworking.canSend(Payloads.C2S.TYPE)) ClientPlayNetworking.send(Payloads.C2S.of(kind, body));
    }

    public static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            String k = String.valueOf(kv[i]);
            if (v instanceof Number n) o.addProperty(k, n);
            else if (v instanceof Boolean b) o.addProperty(k, b);
            else o.addProperty(k, String.valueOf(v));
        }
        return o;
    }
}

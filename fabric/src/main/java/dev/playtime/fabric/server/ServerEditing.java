package dev.playtime.fabric.server;

import com.google.gson.JsonObject;
import dev.playtime.engine.config.WorldConfig;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Applies editor saves after re-checking operator permission. */
public final class ServerEditing {
    private ServerEditing() {}

    private static boolean op(ServerPlayer player) {
        boolean ok = EngineHolder.bridge() != null && EngineHolder.bridge().isOperator(player.getUUID());
        if (!ok) player.displayClientMessage(Component.literal("Operators only."), true);
        return ok;
    }

    public static void saveDevice(ServerPlayer player, JsonObject body) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null || !op(player)) return;
        Id id = Id.tryParse(body.get("id").getAsString());
        Map<String, String> settings = new LinkedHashMap<>();
        if (body.has("settings")) {
            for (String line : body.get("settings").getAsString().split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) settings.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        String name = body.has("name") ? body.get("name").getAsString() : null;
        int inputs = body.has("inputsRequired") ? body.get("inputsRequired").getAsInt() : 1;
        boolean latch = body.has("latch") && body.get("latch").getAsBoolean();
        boolean ok = engine.interactions().configure(id, name, settings, inputs, latch);
        player.displayClientMessage(Component.literal(ok ? "Device saved." : "Unknown device."), true);
    }

    public static void saveJson(ServerPlayer player, JsonObject body) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null || !op(player)) return;
        String mode = body.has("mode") ? body.get("mode").getAsString() : "";
        String json = body.has("json") ? body.get("json").getAsString() : "{}";
        if ("world_config".equals(mode)) {
            try {
                WorldConfig cfg = Json.fromJson(json, WorldConfig.class);
                engine.updateConfig(cfg);
                player.displayClientMessage(Component.literal("World settings saved."), true);
            } catch (RuntimeException e) {
                player.displayClientMessage(Component.literal("World settings not saved: " + e.getMessage()), false);
            }
        } else {
            Editors.saveDefinition(player, body.has("kind") ? body.get("kind").getAsString() : "", json);
        }
    }

    public static void assignProfile(ServerPlayer player, JsonObject body) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) return;
        Id profile = Id.tryParse(body.get("profile").getAsString());
        boolean op = EngineHolder.bridge().isOperator(player.getUUID());
        if (!op && !engine.config().rules.allowConsoleSelfService) {
            player.displayClientMessage(Component.literal("The console is locked."), true);
            return;
        }
        engine.sessions().assignProfile(player.getUUID(), profile);
        player.displayClientMessage(Component.literal("Profile set to " + profile + ". It applies on your next New Game."), false);
    }

    public static void editProfile(ServerPlayer player, JsonObject body) {
        if (!op(player)) return;
        Editors.openDefinitionEditor(player, dev.playtime.engine.content.ContentKind.PROFILES,
                Id.tryParse(body.get("profile").getAsString()));
    }
}

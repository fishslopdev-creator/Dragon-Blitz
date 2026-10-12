package dev.playtime.fabric.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.ConnectionType;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Json;
import dev.playtime.fabric.Ids;
import dev.playtime.fabric.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Opens operator editors on the client. Every save coming back is re-checked for
 * operator permission in {@link dev.playtime.fabric.net.ServerNetworking}.
 */
public final class Editors {
    private Editors() {}

    private static boolean op(ServerPlayer player) {
        if (EngineHolder.bridge() != null && EngineHolder.bridge().isOperator(player.getUUID())) return true;
        player.displayClientMessage(Component.literal("Operators only."), true);
        return false;
    }

    public static void openDeviceEditor(ServerPlayer player, Id deviceId) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null || !op(player)) return;
        Device d = engine.graph().device(deviceId).orElse(null);
        if (d == null) return;
        JsonObject o = new JsonObject();
        o.add("device", Json.toTree(d));
        o.add("state", Json.toTree(engine.graph().state(deviceId)));
        JsonArray links = new JsonArray();
        for (Connection c : engine.graph().outgoing(deviceId)) links.add(describe(c));
        for (Connection c : engine.graph().incoming(deviceId)) links.add(describe(c));
        o.add("links", links);
        ServerPlayNetworking.send(player, Payloads.S2C.of("device_editor", o));
        showLinks(player, deviceId);
    }

    private static JsonObject describe(Connection c) {
        JsonObject l = new JsonObject();
        l.addProperty("id", c.id.toString());
        l.addProperty("text", c.displayName() + ": " + c.from.path() + " -[" + c.type.name().toLowerCase()
                + (c.delayTicks > 0 ? " +" + c.delayTicks + "t" : "") + "]-> " + c.to.path()
                + (c.targetRef != null ? " (" + c.targetRef + ")" : ""));
        return l;
    }

    public static void openConnectionEditor(ServerPlayer player, Id from, Id to) {
        if (!op(player)) return;
        JsonObject o = new JsonObject();
        o.addProperty("from", from.toString());
        o.addProperty("to", to.toString());
        JsonArray types = new JsonArray();
        for (ConnectionType t : ConnectionType.values()) types.add(t.name().toLowerCase());
        o.add("types", types);
        ServerPlayNetworking.send(player, Payloads.S2C.of("connection_editor", o));
    }

    /** Opens the JSON editor for one content definition (new definitions get a template). */
    public static void openDefinitionEditor(ServerPlayer player, ContentKind kind, Id id) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null || !op(player)) return;
        Optional<Object> existing = engine.content().get(kind, id);
        String json;
        if (existing.isPresent()) {
            json = Json.toJson(existing.get());
        } else {
            JsonObject t = new JsonObject();
            t.addProperty("id", id.toString());
            json = Json.toJson(t);
        }
        JsonObject o = new JsonObject();
        o.addProperty("mode", "definition");
        o.addProperty("kind", kind.folder);
        o.addProperty("title", "Edit " + kind.folder + ": " + id);
        o.addProperty("json", json);
        ServerPlayNetworking.send(player, Payloads.S2C.of("json_editor", o));
    }

    public static void openWorldSettings(ServerPlayer player) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null || !op(player)) return;
        JsonObject o = new JsonObject();
        o.addProperty("mode", "world_config");
        o.addProperty("title", "World Settings");
        o.addProperty("json", Json.toJson(engine.config()));
        ServerPlayNetworking.send(player, Payloads.S2C.of("json_editor", o));
    }

    /** Player Setup Console: lists profiles; operators edit them, others only see their own. */
    public static void openSetupConsole(ServerPlayer player) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) return;
        boolean op = EngineHolder.bridge().isOperator(player.getUUID());
        if (!op && !engine.config().rules.allowConsoleSelfService) {
            player.displayClientMessage(Component.literal("The console is locked."), true);
            return;
        }
        JsonObject o = new JsonObject();
        JsonArray profiles = new JsonArray();
        for (var p : engine.profiles().all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", p.id.toString());
            entry.addProperty("name", p.displayName);
            entry.addProperty("default", p.id.equals(engine.config().defaultProfile));
            profiles.add(entry);
        }
        o.add("profiles", profiles);
        o.addProperty("operator", op);
        ServerPlayNetworking.send(player, Payloads.S2C.of("setup_console", o));
    }

    /** Draws particle lines along a device's links for a few seconds (Connection Device preview). */
    public static void showLinks(ServerPlayer player, Id device) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) return;
        ServerLevel level = player.level();
        for (Connection c : engine.graph().outgoing(device)) line(player, level, engine, c, 0xFF22DD66);
        for (Connection c : engine.graph().incoming(device)) line(player, level, engine, c, 0xFFDD8822);
    }

    private static void line(ServerPlayer player, ServerLevel level, PlaytimeEngine engine, Connection c, int color) {
        var a = engine.graph().device(c.from).map(d -> d.pos).orElse(null);
        var b = engine.graph().device(c.to).map(d -> d.pos).orElse(null);
        if (a == null || b == null || !a.dimension().equals(Ids.dimension(level)) || !a.dimension().equals(b.dimension())) return;
        Vec3 from = Ids.mc(a.center());
        Vec3 to = Ids.mc(b.center());
        int steps = (int) Math.min(64, Math.max(2, from.distanceTo(to) * 2));
        DustParticleOptions dust = new DustParticleOptions(color, 1.0F);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(player, dust, true, false, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    static boolean saveDefinition(ServerPlayer player, String kindName, String json) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null || !op(player)) return false;
        ContentKind kind = ContentKind.parse(kindName);
        if (kind == null) {
            player.displayClientMessage(Component.literal("Unknown content kind " + kindName), false);
            return false;
        }
        try {
            var issues = engine.saveDefinition(kind, Json.parse(json).getAsJsonObject());
            report(player, issues);
            return !issues.hasErrors();
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.literal("Invalid JSON: " + e.getMessage()), false);
            return false;
        }
    }

    static void report(ServerPlayer player, dev.playtime.engine.util.IssueList issues) {
        if (issues.isEmpty()) {
            player.displayClientMessage(Component.literal("Saved."), true);
            return;
        }
        for (var i : issues.all()) player.displayClientMessage(Component.literal(i.toString()), false);
    }
}

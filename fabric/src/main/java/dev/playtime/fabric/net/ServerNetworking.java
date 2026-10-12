package dev.playtime.fabric.net;

import com.google.gson.JsonObject;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.ConnectionType;
import dev.playtime.engine.hand.LauncherSide;
import dev.playtime.engine.net.RateLimiter;
import dev.playtime.engine.progress.ProgressionService;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;
import dev.playtime.fabric.server.EngineHolder;
import dev.playtime.fabric.server.ServerEditing;
import dev.playtime.fabric.server.ServerGrabPack;
import dev.playtime.fabric.server.Sync;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;

/**
 * Handles every client request. Requests carry intent only and are validated here:
 * menu actions need an open menu, hand choices must be unlocked, editor saves need operator.
 */
public final class ServerNetworking {
    private static final RateLimiter LIMITER = new RateLimiter(10, 0.5);

    private ServerNetworking() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.C2S.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> handle(player, payload.kind(), payload.body()));
        });
    }

    private static void handle(ServerPlayer player, String kind, JsonObject body) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) return;
        long tick = engine.currentTick();
        if (!LIMITER.tryAcquire(player.getUUID(), kind, tick)) return;
        UUID id = player.getUUID();
        try {
            switch (kind) {
                case "menu" -> menu(player, engine, body);
                case "hand" -> ServerGrabPack.request(player, engine, str(body, "action"), side(body));
                case "select_hand" -> {
                    var r = engine.hands().select(id, side(body), Id.tryParse(str(body, "hand")));
                    if (!r.ok()) player.displayClientMessage(Component.literal(r.message()), true);
                    Sync.sendPlayerState(player);
                }
                case "dialogue" -> engine.chooseDialogueOption(id, body.has("option") ? body.get("option").getAsInt() : -1);
                case "sync_request" -> Sync.sendPlayerState(player);
                case "device_save" -> ServerEditing.saveDevice(player, body);
                case "connect" -> connect(player, engine, body);
                case "link_remove" -> {
                    if (op(player)) engine.interactions().disconnect(Id.tryParse(str(body, "id")));
                }
                case "editor_save" -> ServerEditing.saveJson(player, body);
                case "profile_assign" -> ServerEditing.assignProfile(player, body);
                case "profile_edit" -> ServerEditing.editProfile(player, body);
                default -> { }
            }
        } catch (RuntimeException e) {
            dev.playtime.fabric.PlaytimeMod.LOG.warn("Bad '{}' request from {}: {}", kind, player.getGameProfile().name(), e.toString());
        }
    }

    private static void menu(ServerPlayer player, PlaytimeEngine engine, JsonObject body) {
        UUID id = player.getUUID();
        int slot = body.has("slot") ? body.get("slot").getAsInt() : 0;
        switch (str(body, "action")) {
            case "continue" -> {
                String err = engine.sessions().menuContinue(id, slot);
                if (err != null) player.displayClientMessage(Component.literal(err), true);
                else closeMenu(player);
            }
            case "new_game" -> newGameResult(player, engine.sessions().menuNewGame(id, slot));
            case "confirm" -> newGameResult(player, engine.sessions().menuConfirmNewGame(id, UUID.fromString(str(body, "token"))));
            case "close" -> engine.sessions().menuClosed(id);
            case "chapter" -> {
                // Chapter select replays a reached chapter from its start checkpoint.
                Id chapter = Id.tryParse(str(body, "chapter"));
                var progress = engine.progression().progress(id).orElse(null);
                if (chapter != null && progress != null && engine.menus().isMenuOpen(id)) {
                    engine.sessions().menuClosed(id);
                    engine.startChapter(id, chapter);
                    engine.respawnAtCheckpoint(id, null);
                    closeMenu(player);
                }
            }
            default -> { }
        }
    }

    private static void newGameResult(ServerPlayer player, ProgressionService.NewGameResult r) {
        if (r instanceof ProgressionService.NeedsConfirmation c) {
            JsonObject o = new JsonObject();
            o.addProperty("token", c.ticket().token().toString());
            ServerPlayNetworking.send(player, Payloads.S2C.of("new_game_confirm", o));
        } else if (r instanceof ProgressionService.Denied d) {
            player.displayClientMessage(Component.literal(d.reason()), true);
        } else {
            closeMenu(player);
        }
    }

    private static void closeMenu(ServerPlayer player) {
        ServerPlayNetworking.send(player, Payloads.S2C.of("menu_close", new JsonObject()));
        Sync.sendPlayerState(player);
    }

    private static void connect(ServerPlayer player, PlaytimeEngine engine, JsonObject body) {
        if (!op(player)) return;
        Connection c = new Connection();
        c.from = Id.tryParse(str(body, "from"));
        c.to = Id.tryParse(str(body, "to"));
        c.type = ConnectionType.valueOf(str(body, "type").toUpperCase(Locale.ROOT));
        c.name = str(body, "name").isBlank() ? null : str(body, "name");
        c.delayTicks = body.has("delay") ? Math.max(0, body.get("delay").getAsInt()) : 0;
        c.invert = body.has("invert") && body.get("invert").getAsBoolean();
        c.targetRef = Id.tryParse(str(body, "target"));
        IssueList issues = engine.interactions().connect(c);
        if (issues.hasErrors()) {
            for (var i : issues.all()) player.displayClientMessage(Component.literal(i.message()), false);
        } else {
            player.displayClientMessage(Component.literal("Linked " + c.from.path() + " -> " + c.to.path()
                    + " (" + c.type.name().toLowerCase() + ") as " + c.id), false);
        }
    }

    private static boolean op(ServerPlayer player) {
        return EngineHolder.bridge() != null && EngineHolder.bridge().isOperator(player.getUUID());
    }

    private static LauncherSide side(JsonObject body) {
        return "left".equalsIgnoreCase(str(body, "side")) ? LauncherSide.LEFT : LauncherSide.RIGHT;
    }

    static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}

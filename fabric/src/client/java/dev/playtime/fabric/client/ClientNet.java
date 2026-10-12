package dev.playtime.fabric.client;

import com.google.gson.JsonObject;
import dev.playtime.fabric.client.screen.ConnectionEditorScreen;
import dev.playtime.fabric.client.screen.DeviceEditorScreen;
import dev.playtime.fabric.client.screen.DialogueScreen;
import dev.playtime.fabric.client.screen.JsonEditorScreen;
import dev.playtime.fabric.client.screen.MainMenuScreen;
import dev.playtime.fabric.client.screen.SetupConsoleScreen;
import dev.playtime.fabric.client.screen.TextScreen;
import dev.playtime.fabric.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Dispatches server messages to screens and HUD state (always on the client thread). */
public final class ClientNet {
    private ClientNet() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.S2C.TYPE, (payload, context) ->
                context.client().execute(() -> handle(context.client(), payload.kind(), payload.body())));
    }

    private static void handle(Minecraft mc, String kind, JsonObject body) {
        switch (kind) {
            case "menu" -> {
                if (mc.screen instanceof MainMenuScreen old) old.stopMusic();
                mc.setScreen(new MainMenuScreen(body));
            }
            case "menu_close" -> {
                if (mc.screen instanceof MainMenuScreen menu) menu.closeFromServer();
            }
            case "new_game_confirm" -> {
                if (mc.screen instanceof MainMenuScreen menu) menu.confirmNewGame(ClientState.str(body, "token"));
            }
            case "sync" -> ClientState.sync = body;
            case "objective" -> {
                ClientState.objectiveText = ClientState.str(body, "text");
                ClientState.objectiveTicks = body.has("ticks") ? body.get("ticks").getAsInt() : 0;
                ClientState.objectiveReceivedAt = mc.level == null ? 0 : mc.level.getGameTime();
                ClientSend.send("sync_request", new JsonObject());
            }
            case "unlock" -> {
                ClientState.unlockTitle = ClientState.str(body, "title");
                ClientState.unlockDescription = ClientState.str(body, "description");
                ClientState.unlockUntil = System.currentTimeMillis() + 4000;
            }
            case "dialogue" -> mc.setScreen(new DialogueScreen(body));
            case "dialogue_close" -> {
                if (mc.screen instanceof DialogueScreen) mc.setScreen(null);
            }
            case "recording" -> mc.setScreen(new TextScreen(null, ClientState.str(body, "title"), ClientState.str(body, "text")));
            case "credits" -> mc.setScreen(new TextScreen(null, ClientState.str(body, "title"),
                    ClientState.str(body, "subtitle") + "\n\nThank you for playing.\n\nMade with Playtime Engine.", true));
            case "shake" -> {
                ClientState.shakeTicks = body.get("ticks").getAsInt();
                ClientState.shakeStrength = body.get("strength").getAsFloat();
            }
            case "fade" -> {
                ClientState.fadeTicks = body.get("ticks").getAsInt();
                ClientState.fadeTotal = ClientState.fadeTicks;
            }
            case "cables" -> {
                UUID player = UUID.fromString(ClientState.str(body, "player"));
                List<ClientState.Cable> cables = new ArrayList<>();
                for (JsonObject c : ClientState.array(body, "cables")) {
                    cables.add(new ClientState.Cable(ClientState.str(c, "side"), ClientState.str(c, "hand"),
                            c.get("x").getAsDouble(), c.get("y").getAsDouble(), c.get("z").getAsDouble(), ClientState.str(c, "state")));
                }
                if (cables.isEmpty()) ClientState.CABLES.remove(player);
                else ClientState.CABLES.put(player, cables);
            }
            case "device_editor" -> mc.setScreen(new DeviceEditorScreen(body));
            case "connection_editor" -> mc.setScreen(new ConnectionEditorScreen(body));
            case "json_editor" -> mc.setScreen(new JsonEditorScreen(body));
            case "setup_console" -> mc.setScreen(new SetupConsoleScreen(body));
            default -> { }
        }
    }
}

package dev.playtime.fabric.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.hand.LauncherSide;
import dev.playtime.engine.hand.WheelEntry;
import dev.playtime.engine.progress.ObjectiveStatus;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.story.ChapterDefinition;
import dev.playtime.engine.story.CollectibleDefinition;
import dev.playtime.engine.story.ObjectiveDefinition;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Json;
import dev.playtime.fabric.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Sends the client what its HUD, hand wheel and journal need. */
public final class Sync {
    private Sync() {}

    public static void sendPlayerState(ServerPlayer player) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) return;
        JsonObject o = new JsonObject();
        PlayerProgress p = engine.progression().progress(player.getUUID()).orElse(null);
        o.addProperty("active", engine.config().enabled);
        o.addProperty("wheelMode", engine.config().rules.wheelMode.name().toLowerCase());
        if (p != null) {
            o.addProperty("grabPack", p.grabPack == null ? "" : p.grabPack.toString());
            o.addProperty("left", p.selectedLeftHand == null ? "" : p.selectedLeftHand.toString());
            o.addProperty("right", p.selectedRightHand == null ? "" : p.selectedRightHand.toString());
            o.add("leftWheel", wheel(engine, player, LauncherSide.LEFT));
            o.add("rightWheel", wheel(engine, player, LauncherSide.RIGHT));
            o.addProperty("charged", engine.grabPack().isCharged(player.getUUID()));
            ChapterDefinition chapter = p.chapter == null ? null
                    : engine.content().<ChapterDefinition>get(ContentKind.CHAPTERS, p.chapter).orElse(null);
            o.addProperty("chapter", chapter != null ? chapter.name : p.chapter == null ? "" : p.chapter.path());
            JsonArray objectives = new JsonArray();
            for (Map.Entry<Id, ObjectiveStatus> e : p.objectives.entrySet()) {
                ObjectiveDefinition def = engine.content().<ObjectiveDefinition>get(ContentKind.OBJECTIVES, e.getKey()).orElse(null);
                if (def != null && def.hidden) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("text", def != null ? def.text : e.getKey().path());
                obj.addProperty("status", e.getValue().name().toLowerCase());
                objectives.add(obj);
            }
            o.add("objectives", objectives);
            JsonArray journal = new JsonArray();
            for (Id c : p.collectibles) {
                CollectibleDefinition def = engine.content().<CollectibleDefinition>get(ContentKind.COLLECTIBLES, c).orElse(null);
                if (def == null) continue;
                JsonObject entry = new JsonObject();
                entry.addProperty("title", def.title != null ? def.title : c.path());
                entry.addProperty("kind", def.kind.name().toLowerCase());
                entry.addProperty("text", def.text);
                journal.add(entry);
            }
            o.add("journal", journal);
        }
        ServerPlayNetworking.send(player, Payloads.S2C.of("sync", o));
    }

    private static JsonArray wheel(PlaytimeEngine engine, ServerPlayer player, LauncherSide side) {
        JsonArray arr = new JsonArray();
        for (WheelEntry w : engine.hands().wheel(player.getUUID(), side)) arr.add(Json.toTree(w));
        return arr;
    }
}

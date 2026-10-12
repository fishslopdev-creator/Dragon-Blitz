package dev.playtime.fabric.server;

import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.util.Issue;
import dev.playtime.fabric.PlaytimeMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * Owns the engine for the running world. A new engine is opened from
 * {@code <world>/playtime/} every time a world loads, so each world is its own game.
 */
public final class EngineHolder {
    private static PlaytimeEngine engine;
    private static MinecraftServer server;
    private static FabricGameBridge bridge;

    private EngineHolder() {}

    public static PlaytimeEngine engine() {
        return engine;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static FabricGameBridge bridge() {
        return bridge;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(EngineHolder::open);
        ServerLifecycleEvents.BEFORE_SAVE.register((srv, flush, force) -> {
            if (engine != null) engine.save();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> close());
        ServerTickEvents.END_SERVER_TICK.register(EngineHolder::tick);
    }

    private static void open(MinecraftServer srv) {
        server = srv;
        Path dir = srv.getWorldPath(LevelResource.ROOT).resolve("playtime").normalize();
        bridge = new FabricGameBridge(srv);
        try {
            engine = PlaytimeEngine.open(dir, bridge);
            long errors = engine.loadIssues().count(Issue.Severity.ERROR);
            PlaytimeMod.LOG.info("Playtime Engine opened {} ({} load errors)", dir, errors);
            for (Issue i : engine.loadIssues().all()) {
                if (i.severity() == Issue.Severity.ERROR) PlaytimeMod.LOG.error("{}", i);
                else if (i.severity() == Issue.Severity.WARNING) PlaytimeMod.LOG.warn("{}", i);
            }
        } catch (RuntimeException e) {
            PlaytimeMod.LOG.error("Could not start Playtime Engine for this world; the mod stays inactive", e);
            engine = null;
        }
    }

    private static void close() {
        if (engine != null) {
            try {
                engine.save();
            } catch (RuntimeException e) {
                PlaytimeMod.LOG.error("Failed to save Playtime Engine state", e);
            }
        }
        if (bridge != null) bridge.shutdown();
        engine = null;
        bridge = null;
        server = null;
    }

    private static void tick(MinecraftServer srv) {
        if (engine == null || srv != server) return;
        try {
            engine.tick(srv.overworld().getGameTime());
            bridge.tick();
            ServerGrabPack.tick(srv, engine);
        } catch (RuntimeException e) {
            PlaytimeMod.LOG.error("Playtime Engine tick failed", e);
        }
    }
}

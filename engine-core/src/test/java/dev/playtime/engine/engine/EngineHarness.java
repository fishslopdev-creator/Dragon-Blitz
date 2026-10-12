package dev.playtime.engine.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.playtime.engine.FakeBridge;
import dev.playtime.engine.TestContent;
import dev.playtime.engine.config.WorldConfig;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.ConnectionType;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.menu.PlayMode;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/** Builds an engine with a fake game for integration tests. */
public final class EngineHarness {
    public final FakeBridge game = new FakeBridge();
    public final PlaytimeEngine engine;
    public long tick;
    private int nextX;

    public EngineHarness() {
        this(new WorldConfig());
    }

    public EngineHarness(WorldConfig config) {
        engine = PlaytimeEngine.inMemory(game, config, TestContent.builtins());
    }

    public EngineHarness(Path dir) {
        engine = PlaytimeEngine.open(dir, game);
    }

    public Id device(String id, DeviceType type) {
        return device(id, type, Map.of());
    }

    public Id device(String id, DeviceType type, Map<String, String> settings) {
        return engine.interactions().place(type, BlockRef.of(nextX++ * 2, 64, 0), Id.of(id), settings);
    }

    public void link(String from, String to, ConnectionType type) {
        Connection c = new Connection();
        c.from = Id.of(from);
        c.to = Id.of(to);
        c.type = type;
        IssueList issues = engine.interactions().connect(c);
        if (issues.hasErrors()) throw new AssertionError(issues.toString());
    }

    public IssueList define(ContentKind kind, String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        return engine.saveDefinition(kind, o);
    }

    public UUID join(String name) {
        UUID id = game.add(name).id;
        engine.sessions().onJoin(id, PlayMode.SURVIVAL);
        return id;
    }

    public void tick(int n) {
        for (int i = 0; i < n; i++) engine.tick(++tick);
    }
}

package dev.playtime.engine.engine;

import dev.playtime.engine.config.MenuTrigger;
import dev.playtime.engine.config.ProgressionMode;
import dev.playtime.engine.config.WorldConfig;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.hand.LauncherSide;
import dev.playtime.engine.menu.PlayMode;
import dev.playtime.engine.progress.ProgressionService;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {
    private static Id id(String s) {
        return Id.of(s);
    }

    @Test
    void joiningShowsThisWorldsMenuAndNewGameAppliesTheProfile() {
        WorldConfig c = new WorldConfig();
        c.gameTitle = "Playtime Factory: Chapter 1";
        c.defaultProfile = id("returning_player");
        EngineHarness h = new EngineHarness(c);
        UUID p = h.join("alex");
        var menu = h.game.p(p).lastMenu;
        assertNotNull(menu);
        assertEquals("Playtime Factory: Chapter 1", menu.title());
        assertEquals(MenuTrigger.EVERY_LOGIN, menu.cause());
        assertTrue(menu.slots().get(0).empty());

        assertNotNull(h.engine.sessions().menuContinue(p, 0), "nothing to continue yet");
        var r = h.engine.sessions().menuNewGame(p, 0);
        assertInstanceOf(ProgressionService.Started.class, r);
        assertNotNull(h.game.p(p).kit);
        assertEquals(id("grabpack"), h.game.p(p).kit.grabPack());
        var prog = h.engine.progression().progress(p).orElseThrow();
        assertTrue(prog.hasHand(id("blue_hand")));
        assertEquals(id("blue_hand"), prog.selectedLeftHand);
        assertFalse(h.engine.menus().isMenuOpen(p));
        // Menu actions are refused once the menu is closed (server-side validation).
        assertInstanceOf(ProgressionService.Denied.class, h.engine.sessions().menuNewGame(p, 0));
    }

    @Test
    void gamemodeSwitchAndDeathFollowTheWorldRules() {
        WorldConfig c = new WorldConfig();
        c.mainMenu.showOn = EnumSet.of(MenuTrigger.SURVIVAL_SWITCH);
        EngineHarness h = new EngineHarness(c);
        var fp = h.game.add("alex");
        h.engine.sessions().onJoin(fp.id, PlayMode.CREATIVE);
        assertEquals(0, fp.menusOpened);
        h.tick(100);
        h.engine.sessions().onGameModeChange(fp.id, PlayMode.CREATIVE, PlayMode.SURVIVAL);
        assertEquals(1, fp.menusOpened);
        h.engine.sessions().menuClosed(fp.id);
        h.tick(100);
        h.engine.sessions().onRespawn(fp.id, PlayMode.SURVIVAL, true);
        assertEquals(1, fp.menusOpened, "after-death menu not enabled");
        h.engine.sessions().onDimensionChange(fp.id, PlayMode.SURVIVAL);
        assertEquals(1, fp.menusOpened);
    }

    @Test
    void newGameAskForConfirmationBeforeOverwriting() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.engine.sessions().menuNewGame(p, 0);
        h.engine.grantHand(p, id("blue_hand"), true);
        h.engine.returnToMenu(p);
        var r = h.engine.sessions().menuNewGame(p, 0);
        var ticket = assertInstanceOf(ProgressionService.NeedsConfirmation.class, r).ticket();
        assertTrue(h.engine.progression().progress(p).orElseThrow().hasHand(id("blue_hand")));
        assertInstanceOf(ProgressionService.Started.class, h.engine.sessions().menuConfirmNewGame(p, ticket.token()));
        assertFalse(h.engine.progression().progress(p).orElseThrow().hasHand(id("blue_hand")));
    }

    @Test
    void checkpointsRespawnAndDeathRules(@TempDir Path dir) {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.engine.sessions().menuNewGame(p, 0);
        Id cp = h.device("cp", DeviceType.CHECKPOINT);
        h.engine.interactions().enter(p, cp);
        assertTrue(h.game.p(p).statuses.contains("Checkpoint reached"));
        var respawn = h.engine.sessions().respawnPoint(p).orElseThrow();
        assertEquals(h.engine.graph().device(cp).orElseThrow().pos.y() + 1, respawn.pos().y());
        var outcome = h.engine.sessions().onDeath(p);
        assertTrue(outcome.keepInventory());
        assertEquals(cp, outcome.respawnCheckpoint());
    }

    @Test
    void worldsAreIsolatedAndProgressSurvivesRestart(@TempDir Path root) throws Exception {
        Path worldA = root.resolve("a/playtime");
        Path worldB = root.resolve("b/playtime");
        EngineHarness a = new EngineHarness(worldA);
        WorldConfig ca = a.engine.config();
        ca.gameTitle = "Playtime Factory: Chapter 1";
        a.engine.updateConfig(ca);
        EngineHarness b = new EngineHarness(worldB);
        assertNotEquals("Playtime Factory: Chapter 1", b.engine.config().gameTitle);

        UUID p = a.join("alex");
        a.engine.sessions().menuNewGame(p, 0);
        a.engine.grantHand(p, id("green_hand"), true);
        Id lever = a.device("lever", DeviceType.LEVER);
        a.engine.useDevice(p, lever, null);
        a.define(ContentKind.DOORS, "{\"id\":\"vault\",\"type\":\"locked\"}");
        a.engine.doors().open(id("vault"), 1);
        a.engine.save();
        assertTrue(Files.exists(worldA.resolve("content/doors/vault.json")));
        assertTrue(Files.exists(worldA.resolve("players/" + p + ".json")));

        EngineHarness reopened = new EngineHarness(worldA);
        reopened.game.players.putAll(a.game.players);
        assertEquals("Playtime Factory: Chapter 1", reopened.engine.config().gameTitle);
        assertTrue(reopened.engine.progression().progress(p).orElseThrow().hasHand(id("green_hand")));
        assertTrue(reopened.engine.graph().state(lever).active, "device state survives");
        assertTrue(reopened.engine.doors().isOpen(id("vault")), "door state survives");
        assertTrue(b.engine.progression().progress(p).isEmpty(), "other world has no save for this player");
    }

    @Test
    void sharedProgressionCountsEveryonesWork() {
        WorldConfig c = new WorldConfig();
        c.multiplayer.progressionMode = ProgressionMode.SHARED;
        EngineHarness h = new EngineHarness(c);
        UUID a = h.join("alex");
        UUID b = h.join("blair");
        h.engine.sessions().menuNewGame(a, 0);
        h.engine.grantHand(b, id("red_hand"), true);
        assertTrue(h.engine.progression().progress(a).orElseThrow().hasHand(id("red_hand")));
    }

    @Test
    void grabPackRequiresUnlockedSelectedHand() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.engine.sessions().menuNewGame(p, 0);
        var noPack = h.engine.grabPack().fire(p, LauncherSide.RIGHT, new dev.playtime.engine.util.Vec3(0, 65, 0),
                new dev.playtime.engine.util.Vec3(5, 65, 0), null);
        assertFalse(noPack.fired());
        h.engine.progression().progressOrCreate(p).grabPack = id("grabpack_2");
        assertFalse(h.engine.hands().select(p, LauncherSide.RIGHT, id("flare_hand")).ok());
        h.engine.grantHand(p, id("flare_hand"), true);
        assertTrue(h.engine.grabPack().fire(p, LauncherSide.RIGHT, new dev.playtime.engine.util.Vec3(0, 65, 0),
                new dev.playtime.engine.util.Vec3(5, 65, 0), null).fired());
    }

    @Test
    void pulseHandWakesOnlyNearbyReceivers() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.engine.sessions().menuNewGame(p, 0);
        var prog = h.engine.progression().progressOrCreate(p);
        prog.grabPack = id("grabpack_2");
        h.engine.grantHand(p, id("pulse_hand"), true);
        assertTrue(h.game.p(p).unlocks.contains("Pulse Hand"));
        assertTrue(h.game.p(p).titles.contains("Pulse Hand"), "tutorial shown on first unlock");
        Id near = h.engine.interactions().place(DeviceType.PULSE_RECEIVER, BlockRef.of(10, 64, 0), id("near"), Map.of());
        Id far = h.engine.interactions().place(DeviceType.PULSE_RECEIVER, BlockRef.of(40, 64, 0), id("far"), Map.of());
        var fire = h.engine.grabPack().fire(p, LauncherSide.RIGHT, new dev.playtime.engine.util.Vec3(0.5, 65, 0.5),
                new dev.playtime.engine.util.Vec3(10.5, 64.5, 0.5),
                dev.playtime.engine.grabpack.TargetInfo.surface(new dev.playtime.engine.util.Vec3(10.5, 64.5, 0.5)));
        assertTrue(fire.fired());
        var world = new dev.playtime.engine.grabpack.CableWorld() {
            public boolean isLoaded(dev.playtime.engine.util.Vec3 p1) { return true; }
            public boolean isLineClear(dev.playtime.engine.util.Vec3 a, dev.playtime.engine.util.Vec3 b) { return true; }
            public dev.playtime.engine.grabpack.TargetInfo refresh(dev.playtime.engine.grabpack.TargetInfo t) { return t; }
        };
        var origin = new dev.playtime.engine.util.Vec3(0.5, 65, 0.5);
        java.util.List<HandEffect> effects = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            h.tick(1);
            effects.addAll(h.engine.grabPack().tick(p, origin, origin, world));
        }
        assertTrue(effects.stream().anyMatch(e -> e.type() == HandEffect.Type.PULSE_RING));
        assertTrue(h.engine.graph().state(near).active);
        assertTrue(h.engine.power().isPowered(near));
        assertFalse(h.engine.graph().state(far).active);
        h.tick(120);
        assertFalse(h.engine.graph().state(near).active, "receivers only stay awake briefly");
    }

    @Test
    void dialogueOptionsAreValidatedOnTheServer() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.define(ContentKind.DIALOGUES, """
                {"id":"radio","start":"a","nodes":{
                  "a":{"text":"Hello?","options":[{"text":"Hi","next":"b"},
                       {"text":"Secret","next":"b","conditions":[{"type":"flag","id":"playtime:secret"}]}]},
                  "b":{"text":"Bye","actions":[{"type":"set_flag","id":"playtime:talked"}]}}}
                """);
        h.define(ContentKind.EVENTS, """
                {"id":"after_radio","trigger":{"type":"dialogue_finished","id":"playtime:radio"},
                 "actions":[{"type":"set_flag","id":"playtime:radio_done"}]}
                """);
        h.engine.startDialogue(p, id("radio"));
        assertEquals(1, h.game.p(p).dialogue.options().size(), "hidden option not sent");
        h.engine.chooseDialogueOption(p, 5);
        assertEquals("Hello?", h.game.p(p).dialogue.text(), "invalid index ignored");
        h.engine.chooseDialogueOption(p, 0);
        assertEquals("Bye", h.game.p(p).dialogue.text());
        h.engine.chooseDialogueOption(p, 0);
        assertNull(h.game.p(p).dialogue);
        var prog = h.engine.progression().progress(p).orElseThrow();
        assertTrue(prog.flags.contains(id("talked")));
        assertTrue(prog.flags.contains(id("radio_done")));
    }

    @Test
    void storyEventsRespectRepeatRulesAndRecursionGuard() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.define(ContentKind.EVENTS, """
                {"id":"loop","trigger":{"type":"manual"},"repeat":"always",
                 "actions":[{"type":"give_item","id":"minecraft:stick","value":1},{"type":"trigger_event","id":"playtime:loop"}]}
                """);
        h.define(ContentKind.EVENTS, """
                {"id":"once","trigger":{"type":"manual"},"actions":[{"type":"give_item","id":"minecraft:apple","value":1}]}
                """);
        h.engine.story().fire(id("loop"), p, false);
        int sticks = h.game.p(p).items.get(Id.parse("minecraft:stick"));
        assertTrue(sticks > 1 && sticks <= 24, "self-triggering event is cut off, got " + sticks);
        h.engine.story().fire(id("once"), p, false);
        h.engine.story().fire(id("once"), p, false);
        assertEquals(1, h.game.p(p).items.get(Id.parse("minecraft:apple")));
    }
}

package dev.playtime.engine.puzzle;

import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.engine.EngineHarness;
import dev.playtime.engine.graph.ConnectionType;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.util.Id;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PuzzleTemplatesTest {
    private static Id id(String s) {
        return Id.of(s);
    }

    private static PuzzleStatus status(EngineHarness h, String puzzle) {
        return h.engine.puzzles().state(id(puzzle)).status;
    }

    @Test
    void powerRoutingWithOrderedNodes() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.device("gen", DeviceType.GENERATOR);
        h.device("n1", DeviceType.POWER_NODE, Map.of("needs_activation", "true"));
        h.device("n2", DeviceType.POWER_NODE, Map.of("needs_activation", "true"));
        h.device("rx", DeviceType.POWER_RECEIVER);
        h.link("gen", "n1", ConnectionType.POWER);
        h.link("n1", "n2", ConnectionType.POWER);
        h.link("n2", "rx", ConnectionType.POWER);
        assertFalse(h.define(ContentKind.PUZZLES, """
                {"id":"routing","template":"playtime:power_routing",
                 "settings":{"receivers":["playtime:rx"],"nodes":["playtime:n1","playtime:n2"],"order":["playtime:n1","playtime:n2"]}}
                """).hasErrors());
        h.engine.useDevice(p, id("gen"), null);
        h.tick(1);
        h.engine.signals().setActive(id("n2"), true, p);
        h.tick(1);
        assertFalse(h.engine.graph().state(id("n2")).active, "wrong order resets the nodes");
        h.engine.signals().setActive(id("n1"), true, p);
        h.engine.signals().setActive(id("n2"), true, p);
        h.tick(2);
        assertEquals(PuzzleStatus.COMPLETED, status(h, "routing"));
        assertTrue(h.engine.progression().progress(p).orElseThrow().completedPuzzles.contains(id("routing")));
    }

    @Test
    void fuseCollectionCountsFuses() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.device("box", DeviceType.FUSE_BOX, Map.of("sockets", "a=playtime:fuse,b=playtime:fuse"));
        h.define(ContentKind.PUZZLES, """
                {"id":"fuses","template":"playtime:fuse_collection",
                 "settings":{"fuseBox":"playtime:box","slots":{"a":"playtime:fuse","b":"playtime:fuse"}}}
                """);
        h.game.p(p).items.put(id("fuse"), 2);
        h.engine.useDevice(p, id("box"), id("fuse"));
        assertEquals(PuzzleStatus.ACTIVE, status(h, "fuses"));
        assertTrue(h.game.p(p).statuses.contains("1 / 2 fuses"));
        h.engine.useDevice(p, id("box"), id("fuse"));
        assertEquals(PuzzleStatus.COMPLETED, status(h, "fuses"));
        assertFalse(h.game.p(p).items.containsKey(id("fuse")), "fuses were consumed");
        h.engine.useDevice(p, id("box"), id("wrench"));
        assertEquals(PuzzleStatus.COMPLETED, status(h, "fuses"));
    }

    @Test
    void keycardReaderOpensLinkedDoor() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.define(ContentKind.DOORS, """
                {"id":"lab","type":"keycard","access":{"credentials":["playtime:blue_access_card"]}}
                """);
        h.device("reader", DeviceType.KEYCARD_READER, Map.of("door", "playtime:lab"));
        h.define(ContentKind.PUZZLES, """
                {"id":"card","template":"playtime:keycard","settings":{"reader":"playtime:reader","credentials":["playtime:blue_access_card"]}}
                """);
        h.engine.useDevice(p, id("reader"), null);
        assertFalse(h.engine.doors().isOpen(id("lab")));
        assertTrue(h.game.p(p).statuses.stream().anyMatch(s -> s.contains("Blue Access Card")), h.game.p(p).statuses.toString());
        h.game.p(p).credentials.add(id("blue_access_card"));
        h.engine.useDevice(p, id("reader"), null);
        assertTrue(h.engine.doors().isOpen(id("lab")));
        assertEquals(PuzzleStatus.COMPLETED, status(h, "card"));
    }

    @Test
    void colorSequenceWaitsShowsAndChecksInput() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        for (String b : new String[]{"red", "blue", "green"}) {
            h.device("btn_" + b, DeviceType.BUTTON);
            h.device("lamp_" + b, DeviceType.LIGHT);
        }
        h.define(ContentKind.PUZZLES, """
                {"id":"simon","template":"playtime:color_sequence","maxAttempts":2,"reset":"on_failure",
                 "settings":{"buttons":["playtime:btn_red","playtime:btn_blue","playtime:btn_green"],
                   "lights":{"playtime:btn_red":"playtime:lamp_red","playtime:btn_blue":"playtime:lamp_blue","playtime:btn_green":"playtime:lamp_green"},
                   "sequence":["playtime:btn_green","playtime:btn_red","playtime:btn_green"],"stepTicks":10}}
                """);
        assertTrue(h.engine.puzzles().activate(id("simon"), p));
        h.engine.useDevice(p, id("btn_green"), null);
        assertTrue(h.game.p(p).statuses.contains("Watch the lights first."));
        boolean lampLit = false;
        for (int i = 0; i < 40; i++) {
            h.tick(1);
            lampLit |= h.engine.graph().state(id("lamp_green")).active;
        }
        assertTrue(lampLit, "the sequence is shown on the lamps");
        h.engine.useDevice(p, id("btn_blue"), null);
        assertEquals(1, h.engine.puzzles().state(id("simon")).attempts, "wrong button is a failure");
        h.tick(60);
        h.engine.useDevice(p, id("btn_green"), null);
        h.engine.useDevice(p, id("btn_red"), null);
        h.engine.useDevice(p, id("btn_green"), null);
        assertEquals(PuzzleStatus.COMPLETED, status(h, "simon"));
    }

    @Test
    void timedSwitchesResetOnTimeout() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        for (String s : new String[]{"s1", "s2", "s3"}) h.device(s, DeviceType.LEVER);
        h.define(ContentKind.PUZZLES, """
                {"id":"timed","template":"playtime:timed_switches",
                 "settings":{"switches":["playtime:s1","playtime:s2","playtime:s3"],"windowTicks":40}}
                """);
        h.engine.useDevice(p, id("s1"), null);
        h.engine.useDevice(p, id("s2"), null);
        h.tick(45);
        assertFalse(h.engine.graph().state(id("s1")).active, "switches flipped back after timeout");
        assertEquals(1, h.engine.puzzles().state(id("timed")).attempts);
        h.engine.useDevice(p, id("s1"), null);
        h.tick(5);
        h.engine.useDevice(p, id("s2"), null);
        h.engine.useDevice(p, id("s3"), null);
        assertEquals(PuzzleStatus.COMPLETED, status(h, "timed"));
    }

    @Test
    void traversalInOrder() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.device("pad", DeviceType.TRIGGER_VOLUME);
        h.device("ledge", DeviceType.TRIGGER_VOLUME);
        h.define(ContentKind.PUZZLES, """
                {"id":"cross","template":"playtime:traversal","settings":{"points":["playtime:pad","playtime:ledge"],"ordered":true}}
                """);
        h.engine.interactions().enter(p, id("ledge"));
        assertNotEquals(PuzzleStatus.COMPLETED, status(h, "cross"));
        h.engine.interactions().enter(p, id("pad"));
        h.engine.puzzles().dispatch(PuzzleEvent.reached(id("ledge"), p));
        assertEquals(PuzzleStatus.COMPLETED, status(h, "cross"));
    }

    @Test
    void machineAssemblyNeedsPartsAndPower() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.device("socket", DeviceType.PART_SOCKET, Map.of("sockets", "main=playtime:gear"));
        h.device("lever", DeviceType.LEVER);
        h.device("gen", DeviceType.GENERATOR);
        h.device("press", DeviceType.MACHINE);
        h.link("gen", "press", ConnectionType.POWER);
        h.define(ContentKind.PUZZLES, """
                {"id":"press","template":"playtime:machine_assembly",
                 "settings":{"parts":{"playtime:socket#main":"playtime:gear"},"activator":"playtime:lever","machine":"playtime:press","requiresPower":true}}
                """);
        h.engine.useDevice(p, id("lever"), null);
        assertTrue(h.game.p(p).statuses.stream().anyMatch(s -> s.contains("missing")));
        h.game.p(p).items.put(id("gear"), 1);
        h.engine.useDevice(p, id("socket"), id("gear"));
        h.engine.useDevice(p, id("lever"), null);
        assertTrue(h.game.p(p).statuses.stream().anyMatch(s -> s.contains("no power")));
        h.engine.useDevice(p, id("gen"), null);
        h.tick(1);
        h.engine.useDevice(p, id("lever"), null);
        assertEquals(PuzzleStatus.COMPLETED, status(h, "press"));
    }

    @Test
    void handScannerAndMultiRoom() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.device("scanner", DeviceType.HAND_SCANNER, Map.of("hands", "playtime:green_hand"));
        h.device("valve", DeviceType.LEVER);
        h.define(ContentKind.PUZZLES, """
                {"id":"scan","template":"playtime:hand_scanner","settings":{"scanner":"playtime:scanner","hands":["playtime:green_hand"]}}
                """);
        h.define(ContentKind.PUZZLES, """
                {"id":"hub","template":"playtime:multi_room","settings":{"active":["playtime:valve"],"puzzles":["playtime:scan"]}}
                """);
        h.engine.puzzles().dispatch(PuzzleEvent.scan(id("scanner"), id("red_hand"), Set.of(id("red_scanner")), p));
        assertEquals(PuzzleStatus.ACTIVE, status(h, "scan"));
        h.engine.useDevice(p, id("valve"), null);
        assertEquals(PuzzleStatus.ACTIVE, status(h, "hub"));
        h.engine.puzzles().dispatch(PuzzleEvent.scan(id("scanner"), id("green_hand"), Set.of(), p));
        assertEquals(PuzzleStatus.COMPLETED, status(h, "scan"));
        assertEquals(PuzzleStatus.COMPLETED, status(h, "hub"), "completing a room puzzle notifies the hub");
    }

    @Test
    void completionIsIdempotentForSharedPuzzles() {
        EngineHarness h = new EngineHarness();
        UUID a = h.join("alex");
        UUID b = h.join("blair");
        h.device("btn", DeviceType.BUTTON);
        h.define(ContentKind.PUZZLES, """
                {"id":"once","template":"playtime:traversal","settings":{"points":["playtime:btn"]},
                 "onComplete":[{"type":"give_item","id":"minecraft:diamond","value":1}]}
                """);
        h.engine.useDevice(a, id("btn"), null);
        h.engine.useDevice(b, id("btn"), null);
        h.engine.useDevice(a, id("btn"), null);
        assertEquals(1, h.game.p(a).items.get(Id.parse("minecraft:diamond")), "reward given exactly once");
        assertNull(h.game.p(b).items.get(Id.parse("minecraft:diamond")));
    }

    @Test
    void activationConditionsGatePuzzles() {
        EngineHarness h = new EngineHarness();
        UUID p = h.join("alex");
        h.device("btn", DeviceType.BUTTON);
        h.define(ContentKind.PUZZLES, """
                {"id":"gated","template":"playtime:traversal","settings":{"points":["playtime:btn"]},
                 "activation":[{"type":"flag","id":"playtime:power_on"}]}
                """);
        h.engine.useDevice(p, id("btn"), null);
        assertEquals(PuzzleStatus.INACTIVE, status(h, "gated"));
        h.engine.progression().progressOrCreate(p).flags.add(id("power_on"));
        h.engine.useDevice(p, id("btn"), null);
        assertEquals(PuzzleStatus.COMPLETED, status(h, "gated"));
    }

    @Test
    void unknownTemplateIsReportedNotFatal() {
        EngineHarness h = new EngineHarness();
        assertTrue(h.define(ContentKind.PUZZLES, "{\"id\":\"bad\",\"template\":\"playtime:nope\"}").hasErrors());
    }
}

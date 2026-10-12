package dev.playtime.engine.sample;

import dev.playtime.engine.engine.EngineHarness;
import dev.playtime.engine.engine.HandEffect;
import dev.playtime.engine.grabpack.CableWorld;
import dev.playtime.engine.grabpack.TargetInfo;
import dev.playtime.engine.hand.LauncherSide;
import dev.playtime.engine.progress.ObjectiveStatus;
import dev.playtime.engine.puzzle.PuzzleStatus;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Issue;
import dev.playtime.engine.util.IssueList;
import dev.playtime.engine.util.Vec3;
import dev.playtime.engine.validate.MapValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plays the whole sample game headlessly: Pulse Hand pickup, the Assembly Line (all
 * stages), a server restart in the middle, the optional Playwright encounter, a failed
 * Factory Shutdown chase, the retry, and the ending.
 */
class SamplePlaythroughTest {
    static final int OX = 100, OY = 64, OZ = -50;

    static Id s(String path) {
        return Id.parse("playtime:sample/" + path);
    }

    static Vec3 rel(int x, int y, int z) {
        return new Vec3(OX + x + 0.5, OY + y + 0.5, OZ + z + 0.5);
    }

    static final CableWorld OPEN_WORLD = new CableWorld() {
        @Override public boolean isLoaded(Vec3 position) { return true; }
        @Override public boolean isLineClear(Vec3 from, Vec3 to) { return true; }
        @Override public TargetInfo refresh(TargetInfo target) { return target; }
    };

    @Test
    void sampleInstallsAndValidatesCleanly(@TempDir Path dir) {
        EngineHarness h = new EngineHarness(dir.resolve("playtime"));
        var result = SampleScenario.install(h.engine, BlockRef.OVERWORLD, OX, OY, OZ, true);
        assertFalse(result.issues().hasErrors(), result.issues().toString());
        assertEquals(23, result.devices().size());
        assertTrue(Files.exists(dir.resolve("playtime/content/puzzles/sample/assembly_line.json")),
                "sample content is saved as ordinary editable map files");
        IssueList issues = MapValidator.validate(h.engine);
        assertEquals(0, issues.count(Issue.Severity.ERROR), issues.toString());
    }

    @Test
    void fullPlaythroughWithRestartFailureAndRetry(@TempDir Path dir) {
        Path world = dir.resolve("playtime");
        EngineHarness h = new EngineHarness(world);
        SampleScenario.install(h.engine, BlockRef.OVERWORLD, OX, OY, OZ, true);
        UUID p = h.join("alex");
        var player = h.game.p(p);
        assertEquals("Playtime Engine", player.lastMenu.title());

        // New Game: Factory Visitor profile, sample chapter, entrance checkpoint.
        h.engine.sessions().menuNewGame(p, 0);
        var prog = h.engine.progression().progress(p).orElseThrow();
        assertEquals(Id.of("grabpack_2"), prog.grabPack);
        assertEquals(s("factory"), prog.chapter);
        assertEquals(s("start"), prog.checkpoint);
        assertTrue(player.titles.contains("The Assembly Floor"));
        assertEquals(ObjectiveStatus.ACTIVE, prog.objectives.get(s("find_pulse_hand")));

        // Pulse Hand pickup.
        h.engine.useDevice(p, s("pulse_pickup"), null);
        assertTrue(prog.hasHand(Id.of("pulse_hand")));
        assertEquals(Id.of("pulse_hand"), prog.selectedRightHand);
        assertEquals(ObjectiveStatus.COMPLETED, prog.objectives.get(s("find_pulse_hand")));

        // The passage is sealed until the puzzle is done.
        assertFalse(h.engine.interactions().tryOpenDoor(p, s("factory_passage")).granted());

        // Stage 1: find the core and slot it in.
        h.engine.useDevice(p, s("core_shelf"), null);
        assertEquals(1, player.items.get(Id.of("power_core")));
        h.engine.useDevice(p, s("al_generator"), Id.of("power_core"));
        assertNull(player.items.get(Id.of("power_core")), "core consumed");
        assertEquals(1, h.engine.puzzles().state(s("assembly_line")).stage);

        // Stage 2: start the generator; the panel receives power.
        h.engine.useDevice(p, s("al_generator"), null);
        h.tick(2);
        assertTrue(h.engine.power().isPowered(s("al_panel")));
        assertEquals(2, h.engine.puzzles().state(s("assembly_line")).stage);

        // ---- server restart in the middle of the puzzle ----
        h.engine.save();
        long tick = h.tick;
        EngineHarness r = new EngineHarness(world);
        r.game.players.putAll(h.game.players);
        r.tick = tick;
        h = r;
        player = h.game.p(p);
        prog = h.engine.progression().progress(p).orElseThrow();
        assertEquals(2, h.engine.puzzles().state(s("assembly_line")).stage, "puzzle stage survives a restart");
        h.tick(2);
        assertTrue(h.engine.power().isPowered(s("al_panel")), "power is recomputed after loading");

        // Stage 3: one pulse between the receivers wakes both at once, driving the line.
        Vec3 origin = rel(14, 2, 0);
        var fire = h.engine.grabPack().fire(p, LauncherSide.RIGHT, origin, rel(16, 1, 0), TargetInfo.surface(rel(16, 1, 0)));
        assertTrue(fire.fired(), String.valueOf(fire.message()));
        List<HandEffect> effects = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            h.tick(1);
            effects.addAll(h.engine.grabPack().tick(p, origin, origin, OPEN_WORLD));
        }
        assertTrue(effects.stream().anyMatch(e -> e.type() == HandEffect.Type.PULSE_RING));
        assertTrue(h.engine.graph().state(s("al_conveyor")).active, "both receivers drove the two-input machine");
        assertEquals(3, h.engine.puzzles().state(s("assembly_line")).stage);

        // Stage 4: valves too slowly -> stage restarts; then quickly -> done.
        h.engine.useDevice(p, s("al_valve_a"), null);
        h.tick(200);
        assertFalse(h.engine.graph().state(s("al_valve_a")).active, "valve slammed shut after the timer");
        assertEquals(3, h.engine.puzzles().state(s("assembly_line")).stage, "a failed stage restarts itself");
        h.engine.useDevice(p, s("al_valve_a"), null);
        h.engine.useDevice(p, s("al_valve_b"), null);
        h.engine.useDevice(p, s("al_valve_c"), null);
        assertEquals(PuzzleStatus.COMPLETED, h.engine.puzzles().state(s("assembly_line")).status);
        assertTrue(prog.completedPuzzles.contains(s("assembly_line")));
        assertTrue(h.engine.doors().isOpen(s("factory_passage")), "the passage opens");

        // Optional encounter: The Playwright spawns at its marker shortly after.
        h.tick(50);
        assertEquals(1, h.game.monsters.size());
        assertEquals(Id.of("the_playwright"), h.game.monsters.values().iterator().next().id);

        // Factory Shutdown: pull the lever.
        h.engine.useDevice(p, s("shutdown_lever"), null);
        assertTrue(h.engine.chases().isActive(s("factory_shutdown")));
        assertTrue(player.titles.contains("FACTORY SHUTDOWN"));
        assertTrue(h.game.lights.contains("off"));
        h.tick(45);
        assertEquals(1, h.game.monsters.size(), "patrol monster replaced by the chase monster");
        UUID monster = h.game.monsters.keySet().iterator().next();

        // Caught: chase fails, player returns to the checkpoint, lever resets for a retry.
        h.engine.director().onAttack(monster, p);
        assertFalse(h.engine.chases().isActive(s("factory_shutdown")));
        BlockRef cp = player.teleports.get(player.teleports.size() - 1);
        assertEquals(new BlockRef(BlockRef.OVERWORLD, OX + 32, OY + 2, OZ), cp);
        assertFalse(h.engine.graph().state(s("shutdown_lever")).active);
        assertTrue(h.game.monsters.isEmpty());

        // Retry and escape: flip the breaker, start the backup generator, run for the exit.
        h.engine.useDevice(p, s("shutdown_lever"), null);
        assertTrue(h.engine.chases().isActive(s("factory_shutdown")));
        h.tick(45);
        h.engine.useDevice(p, s("backup_breaker"), null);
        h.engine.useDevice(p, s("backup_generator"), null);
        h.tick(2);
        assertTrue(h.engine.power().isPowered(s("exit_door_controller")));
        assertTrue(h.engine.doors().isOpen(s("exit_door")), "powered security door opened");
        h.tick(1);
        assertEquals(1, h.engine.chases().active().iterator().next().stage, "now in the escape stage");
        h.engine.interactions().enter(p, s("exit_volume"));
        h.tick(1);
        assertFalse(h.engine.chases().isActive(s("factory_shutdown")));
        assertTrue(prog.completedChases.contains(s("factory_shutdown")));
        assertTrue(player.titles.contains("You made it out"));
        assertTrue(player.titles.contains("credits"));
        assertTrue(h.game.monsters.isEmpty(), "chase monster despawned");
        assertTrue(h.game.lights.get(h.game.lights.size() - 1).equals("on"));

        // Non-repeatable: pulling the lever again does nothing.
        h.engine.useDevice(p, s("shutdown_lever"), null);
        h.engine.useDevice(p, s("shutdown_lever"), null);
        assertFalse(h.engine.chases().isActive(s("factory_shutdown")));

        // Everything is persisted.
        h.engine.save();
        EngineHarness again = new EngineHarness(world);
        var saved = again.engine.progression().progress(p).orElseThrow();
        assertTrue(saved.completedChases.contains(s("factory_shutdown")));
        assertTrue(saved.hasHand(Id.of("pulse_hand")));
        assertTrue(again.engine.puzzles().isCompleted(s("assembly_line")));
    }
}

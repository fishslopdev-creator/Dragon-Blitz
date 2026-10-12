package dev.playtime.engine.progress;

import dev.playtime.engine.config.ProgressionMode;
import dev.playtime.engine.config.ResetAuthority;
import dev.playtime.engine.config.WorldConfig;
import dev.playtime.engine.util.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgressionServiceTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void newGameOnEmptySlotStartsImmediately() {
        var svc = new ProgressionService(new WorldConfig(), new PlayerSaveStore(null), null);
        var r = svc.requestNewGame(a, 0, false, 0);
        assertInstanceOf(ProgressionService.Started.class, r);
    }

    @Test
    void newGameOverExistingSaveNeedsConfirmation() {
        var svc = new ProgressionService(new WorldConfig(), new PlayerSaveStore(null), null);
        svc.progressOrCreate(a).unlockHand(Id.of("blue_hand"));
        var r = svc.requestNewGame(a, 0, false, 0);
        var ticket = assertInstanceOf(ProgressionService.NeedsConfirmation.class, r).ticket();
        assertTrue(svc.progress(a).orElseThrow().hasHand(Id.of("blue_hand")), "nothing destroyed before confirming");
        assertInstanceOf(ProgressionService.Denied.class, svc.confirmNewGame(a, UUID.randomUUID(), false, 1));
        // wrong token consumed nothing? the ticket is single use, so request again
        ticket = assertInstanceOf(ProgressionService.NeedsConfirmation.class, svc.requestNewGame(a, 0, false, 2)).ticket();
        var started = assertInstanceOf(ProgressionService.Started.class, svc.confirmNewGame(a, ticket.token(), false, 3));
        assertFalse(started.progress().hasHand(Id.of("blue_hand")));
    }

    @Test
    void confirmationExpires() {
        var svc = new ProgressionService(new WorldConfig(), new PlayerSaveStore(null), null);
        svc.progressOrCreate(a).reachCheckpoint(Id.of("cp"));
        var t = ((ProgressionService.NeedsConfirmation) svc.requestNewGame(a, 0, false, 0)).ticket();
        assertInstanceOf(ProgressionService.Denied.class,
                svc.confirmNewGame(a, t.token(), false, ProgressionService.NEW_GAME_CONFIRM_TICKS + 1));
        assertEquals(Id.of("cp"), svc.progress(a).orElseThrow().checkpoint);
    }

    @Test
    void slotsAreIndependent() {
        WorldConfig c = new WorldConfig();
        c.saveSlots = 3;
        var svc = new ProgressionService(c, new PlayerSaveStore(null), null);
        svc.progressOrCreate(a).unlockHand(Id.of("red_hand"));
        svc.requestNewGame(a, 2, false, 0);
        assertFalse(svc.progress(a).orElseThrow().hasHand(Id.of("red_hand")));
        svc.continueGame(a, 0);
        assertTrue(svc.progress(a).orElseThrow().hasHand(Id.of("red_hand")));
    }

    @Test
    void sharedModeUsesOneSaveForEveryone() {
        WorldConfig c = new WorldConfig();
        c.multiplayer.progressionMode = ProgressionMode.SHARED;
        var svc = new ProgressionService(c, new PlayerSaveStore(null), null);
        svc.progressOrCreate(a).unlockHand(Id.of("green_hand"));
        assertTrue(svc.progress(b).orElseThrow().hasHand(Id.of("green_hand")));
    }

    @Test
    void hostOwnedModeRestrictsNewGame() {
        WorldConfig c = new WorldConfig();
        c.multiplayer.progressionMode = ProgressionMode.HOST_OWNED;
        c.multiplayer.hostPlayer = a;
        c.multiplayer.resetAuthority = ResetAuthority.HOST_AND_OPERATORS;
        var svc = new ProgressionService(c, new PlayerSaveStore(null), null);
        assertInstanceOf(ProgressionService.Denied.class, svc.requestNewGame(b, 0, false, 0));
        assertInstanceOf(ProgressionService.Started.class, svc.requestNewGame(a, 0, false, 0));
        assertTrue(svc.mayReset(b, true), "operators can always reset");
    }

    @Test
    void deathRollsBackUncheckpointedHandsWhenConfigured() {
        WorldConfig c = new WorldConfig();
        c.rules.keepHandsOnDeath = false;
        c.rules.keepUncheckpointedKeysOnDeath = false;
        var svc = new ProgressionService(c, new PlayerSaveStore(null), null);
        PlayerProgress p = svc.progressOrCreate(a);
        p.unlockHand(Id.of("blue_hand"));
        p.reachCheckpoint(Id.of("cp1"));
        p.unlockHand(Id.of("red_hand"));
        p.addKey(Id.of("blue_access_card"));
        p.selectedRightHand = Id.of("red_hand");
        DeathOutcome d = svc.onDeath(a, null, null);
        assertTrue(d.lostHands().contains(Id.of("red_hand")));
        assertTrue(p.hasHand(Id.of("blue_hand")), "checkpointed unlocks survive");
        assertFalse(p.hasHand(Id.of("red_hand")));
        assertNull(p.selectedRightHand);
        assertFalse(p.keys.contains(Id.of("blue_access_card")));
        assertEquals(Id.of("cp1"), d.respawnCheckpoint());
    }

    @Test
    void profileOverridesWorldDeathRule() {
        WorldConfig c = new WorldConfig();
        c.rules.keepHandsOnDeath = true;
        var svc = new ProgressionService(c, new PlayerSaveStore(null), null);
        svc.progressOrCreate(a).unlockHand(Id.of("blue_hand"));
        DeathOutcome d = svc.onDeath(a, false, false);
        assertFalse(d.keepInventory());
        assertTrue(d.lostHands().contains(Id.of("blue_hand")));
    }

    @Test
    void savesSurviveRestartAndCorruptionIsQuarantined(@TempDir Path dir) throws Exception {
        WorldConfig c = new WorldConfig();
        var store = new PlayerSaveStore(dir);
        var svc = new ProgressionService(c, store, null);
        svc.progressOrCreate(a).unlockHand(Id.of("purple_hand"));
        svc.markDirty(a);
        store.saveAll();

        var reloaded = new ProgressionService(c, new PlayerSaveStore(dir), null);
        assertTrue(reloaded.progress(a).orElseThrow().hasHand(Id.of("purple_hand")));

        Files.writeString(dir.resolve(b + ".json"), "{ not json");
        var store2 = new PlayerSaveStore(dir);
        assertNotNull(store2.get(b), "corrupt save replaced by a fresh one");
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(f -> f.getFileName().toString().startsWith(b + ".json.corrupt")));
        }
    }

    @Test
    void migratorUpgradesOldSchemas() {
        var m = new SaveMigrator("test", 2).step(0, j -> {
            j.addProperty("added", true);
            return j;
        });
        var json = new com.google.gson.JsonObject();
        var r = m.migrate(json);
        assertTrue(r.changed());
        assertEquals(2, r.json().get("schemaVersion").getAsInt());
        assertTrue(r.json().get("added").getAsBoolean());
        var future = new com.google.gson.JsonObject();
        future.addProperty("schemaVersion", 9);
        assertNotNull(m.migrate(future).warning());
    }
}

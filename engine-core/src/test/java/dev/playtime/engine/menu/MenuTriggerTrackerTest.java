package dev.playtime.engine.menu;

import dev.playtime.engine.config.MenuTrigger;
import dev.playtime.engine.config.WorldConfig;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MenuTriggerTrackerTest {
    private final UUID p = UUID.randomUUID();

    private WorldConfig config(MenuTrigger... triggers) {
        WorldConfig c = new WorldConfig();
        c.mainMenu.showOn = triggers.length == 0 ? EnumSet.noneOf(MenuTrigger.class) : EnumSet.of(triggers[0], triggers);
        return c;
    }

    @Test
    void joinInSurvivalShowsMenuOnce() {
        var t = new MenuTriggerTracker();
        WorldConfig c = config(MenuTrigger.EVERY_LOGIN);
        assertTrue(t.onJoin(c, p, PlayMode.SURVIVAL, false, 0).show());
        // A duplicate event while the menu is open must not reopen it.
        assertFalse(t.onRespawn(c, p, PlayMode.SURVIVAL, true, 5).show());
        assertFalse(t.onGameModeChange(c, p, PlayMode.CREATIVE, PlayMode.SURVIVAL, 6).show());
    }

    @Test
    void joinInCreativeDoesNotShow() {
        var t = new MenuTriggerTracker();
        assertFalse(t.onJoin(config(MenuTrigger.EVERY_LOGIN), p, PlayMode.CREATIVE, false, 0).show());
    }

    @Test
    void gamemodeSurvivalShowsWhenEnabled() {
        var t = new MenuTriggerTracker();
        WorldConfig c = config(MenuTrigger.SURVIVAL_SWITCH);
        t.onJoin(c, p, PlayMode.CREATIVE, false, 0);
        MenuDecision d = t.onGameModeChange(c, p, PlayMode.CREATIVE, PlayMode.SURVIVAL, 100);
        assertTrue(d.show());
        assertEquals(MenuTrigger.SURVIVAL_SWITCH, d.cause());
        t.onMenuClosed(p);
        // survival -> survival is not a switch
        assertFalse(t.onGameModeChange(c, p, PlayMode.SURVIVAL, PlayMode.SURVIVAL, 300).show());
    }

    @Test
    void gamemodeSwitchIgnoredWhenDisabled() {
        var t = new MenuTriggerTracker();
        WorldConfig c = config(MenuTrigger.EVERY_LOGIN);
        assertFalse(t.onGameModeChange(c, p, PlayMode.CREATIVE, PlayMode.SURVIVAL, 100).show());
    }

    @Test
    void respawnOnlyAfterDeathAndOnlyWhenConfigured() {
        var t = new MenuTriggerTracker();
        WorldConfig c = config(MenuTrigger.AFTER_DEATH);
        assertFalse(t.onRespawn(c, p, PlayMode.SURVIVAL, false, 100).show(), "end-portal respawn");
        assertTrue(t.onRespawn(c, p, PlayMode.SURVIVAL, true, 200).show());
        WorldConfig none = config(MenuTrigger.EVERY_LOGIN);
        t.onMenuClosed(p);
        assertFalse(t.onRespawn(none, p, PlayMode.SURVIVAL, true, 400).show());
    }

    @Test
    void dimensionChangeNeverShows() {
        var t = new MenuTriggerTracker();
        assertFalse(t.onDimensionChange(p, PlayMode.SURVIVAL).show());
    }

    @Test
    void firstEntryOnlyShowsForNewPlayers() {
        var t = new MenuTriggerTracker();
        WorldConfig c = config(MenuTrigger.FIRST_ENTRY);
        assertTrue(t.onJoin(c, p, PlayMode.SURVIVAL, true, 0).show());
        t.onDisconnect(p);
        assertFalse(t.onJoin(c, p, PlayMode.SURVIVAL, false, 1000).show());
    }

    @Test
    void adminBypassSkipsMenu() {
        var t = new MenuTriggerTracker();
        t.setBypass(p, true);
        assertFalse(t.onJoin(config(MenuTrigger.EVERY_LOGIN), p, PlayMode.SURVIVAL, false, 0).show());
    }

    @Test
    void rapidTriggersAreDeduplicated() {
        var t = new MenuTriggerTracker();
        WorldConfig c = config(MenuTrigger.EVERY_LOGIN, MenuTrigger.SURVIVAL_SWITCH);
        assertTrue(t.onJoin(c, p, PlayMode.SURVIVAL, false, 0).show());
        t.onMenuClosed(p);
        assertFalse(t.onGameModeChange(c, p, PlayMode.CREATIVE, PlayMode.SURVIVAL, 10).show());
        assertTrue(t.onGameModeChange(c, p, PlayMode.CREATIVE, PlayMode.SURVIVAL, 100).show());
    }

    @Test
    void adventureCountsOnlyWhenEnabled() {
        var t = new MenuTriggerTracker();
        WorldConfig c = config(MenuTrigger.EVERY_LOGIN);
        assertFalse(t.onJoin(c, p, PlayMode.ADVENTURE, false, 0).show());
        c.mainMenu.includeAdventureMode = true;
        t.onDisconnect(p);
        assertTrue(t.onJoin(c, p, PlayMode.ADVENTURE, false, 100).show());
    }

    @Test
    void disabledEngineNeverShows() {
        var t = new MenuTriggerTracker();
        WorldConfig c = config(MenuTrigger.EVERY_LOGIN);
        c.enabled = false;
        assertFalse(t.onJoin(c, p, PlayMode.SURVIVAL, true, 0).show());
    }
}

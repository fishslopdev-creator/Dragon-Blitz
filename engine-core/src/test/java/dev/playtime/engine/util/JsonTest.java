package dev.playtime.engine.util;

import dev.playtime.engine.config.MenuTrigger;
import dev.playtime.engine.config.WorldConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test
    void identifiersDefaultToPlaytimeNamespace() {
        assertEquals(new Id("playtime", "blue_hand"), Id.parse("blue_hand"));
        assertEquals(new Id("mymap", "doors/a"), Id.parse("mymap:doors/a"));
        assertThrows(IllegalArgumentException.class, () -> Id.parse("Bad:ID"));
        assertNull(Id.tryParse("no spaces allowed"));
    }

    @Test
    void enumsAreLowerCaseAndForgiving() {
        WorldConfig c = Json.fromJson("{\"mainMenu\":{\"showOn\":[\"every_login\",\"Survival Switch\"]}}", WorldConfig.class);
        assertTrue(c.mainMenu.showOn.contains(MenuTrigger.EVERY_LOGIN));
        assertTrue(c.mainMenu.showOn.contains(MenuTrigger.SURVIVAL_SWITCH));
        assertTrue(Json.toJson(c).contains("\"every_login\""));
    }

    @Test
    void unknownEnumValueIsAClearError() {
        var e = assertThrows(RuntimeException.class,
                () -> Json.fromJson("{\"mainMenu\":{\"showOn\":[\"sometimes\"]}}", WorldConfig.class));
        assertTrue(e.getMessage().contains("sometimes"));
    }

    @Test
    void partialConfigGetsDefaults() {
        WorldConfig c = Json.fromJson("{\"gameTitle\":\"Playtime Factory: Chapter 1\",\"mainMenu\":null}", WorldConfig.class).normalize();
        assertEquals("Playtime Factory: Chapter 1", c.menuTitle());
        assertNotNull(c.mainMenu.buttons);
        assertFalse(c.mainMenu.orderedVisibleButtons().isEmpty());
    }
}

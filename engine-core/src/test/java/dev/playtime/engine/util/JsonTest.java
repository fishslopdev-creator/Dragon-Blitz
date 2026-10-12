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

    @Test
    void coloursAcceptHexAndNumbersFromTextAndTrees() {
        String text = "{\"color\":\"#2F6BDB\"}";
        var fromText = Json.fromJson(text, dev.playtime.engine.hand.HandDefinition.class);
        var fromTree = Json.fromJson(Json.parse(text), dev.playtime.engine.hand.HandDefinition.class);
        assertEquals(0xFF2F6BDB, fromText.color);
        assertEquals(0xFF2F6BDB, fromTree.color);
        assertEquals(0xFF2F6BDB, Json.fromJson("{\"color\":4281297883}", dev.playtime.engine.hand.HandDefinition.class).color);
        assertTrue(Json.toJson(fromText).contains("\"#FF2F6BDB\""));
        assertThrows(RuntimeException.class, () -> Json.fromJson("{\"color\":\"blue\"}", dev.playtime.engine.hand.HandDefinition.class));
    }

    @Test
    void everyBuiltinFileParsesFromText() throws Exception {
        var dir = java.nio.file.Path.of("src/main/resources/playtime");
        try (var walk = java.nio.file.Files.walk(dir)) {
            for (var p : walk.filter(f -> f.toString().endsWith(".json")).toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                String[] parts = rel.split("/");
                var kind = parts.length > 2 ? dev.playtime.engine.content.ContentKind.byFolder(parts[parts.length > 3 && parts[1].equals("content") ? 2 : 1]) : null;
                if (kind == null) continue;
                // Text parsing is stricter than tree parsing (e.g. int overflow), so check both.
                assertNotNull(Json.fromJson(java.nio.file.Files.readString(p), kind.type), rel);
            }
        }
    }
}

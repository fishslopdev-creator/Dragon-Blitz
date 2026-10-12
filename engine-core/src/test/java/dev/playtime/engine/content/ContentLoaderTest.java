package dev.playtime.engine.content;

import com.google.gson.JsonParser;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ContentLoaderTest {
    @Test
    void builtinIndexListsEveryBuiltinFile() throws Exception {
        Path dir = Path.of("src/main/resources/playtime/builtin");
        List<String> onDisk = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".json") && !p.getFileName().toString().equals("index.json"))
                    .forEach(p -> onDisk.add(dir.relativize(p).toString().replace('\\', '/')));
        }
        List<String> indexed = new ArrayList<>();
        try (InputStream in = ContentLoader.class.getResourceAsStream(ContentLoader.BUILTIN_INDEX)) {
            JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()
                    .forEach(e -> indexed.add(e.getAsString()));
        }
        onDisk.sort(null);
        indexed.sort(null);
        assertEquals(onDisk, indexed, "run tools/update_builtin_index.py after adding built-in files");
    }

    @Test
    void builtinsLoadCleanly() {
        IssueList issues = new IssueList();
        ContentPack pack = ContentLoader.load(null, true, issues);
        assertFalse(issues.hasErrors(), issues.toString());
        assertEquals(9, pack.size(ContentKind.HANDS));
        assertTrue(pack.size(ContentKind.MONSTERS) >= 20);
        assertTrue(pack.contains(ContentKind.MONSTERS, Id.of("the_playwright")));
    }

    @Test
    void worldContentOverridesAndBrokenFilesAreIsolated(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("hands"));
        Files.writeString(dir.resolve("hands/blue_hand.json"), "{\"id\":\"playtime:blue_hand\",\"displayName\":\"Map Blue\"}");
        Files.writeString(dir.resolve("hands/broken.json"), "{ this is not json");
        Files.createDirectories(dir.resolve("doors"));
        Files.writeString(dir.resolve("doors/vault.json"), "{\"type\":\"keycard\"}");
        Files.writeString(dir.resolve("doors/badtype.json"), "{\"type\":\"teleporter\"}");
        Files.createDirectories(dir.resolve("unknown"));
        Files.writeString(dir.resolve("unknown/x.json"), "{}");

        IssueList issues = new IssueList();
        ContentPack pack = ContentLoader.load(dir, true, issues);
        assertEquals("Map Blue", pack.<dev.playtime.engine.hand.HandDefinition>get(ContentKind.HANDS, Id.of("blue_hand")).orElseThrow().displayName);
        assertTrue(pack.contains(ContentKind.DOORS, Id.of("vault")), "id defaults to the file name");
        assertEquals(2, issues.count(dev.playtime.engine.util.Issue.Severity.ERROR), issues.toString());
        assertTrue(issues.toString().contains("broken.json"));
        assertTrue(issues.toString().contains("teleporter"));
        assertEquals(9, pack.size(ContentKind.HANDS), "the rest of the map still loads");
    }

    @Test
    void writeRefusesPathTraversal(@TempDir Path dir) {
        var def = new dev.playtime.engine.access.DoorDefinition();
        def.id = new Id("playtime", "../../escape");
        assertThrows(java.io.IOException.class, () -> ContentLoader.write(dir, ContentKind.DOORS, def));
    }
}

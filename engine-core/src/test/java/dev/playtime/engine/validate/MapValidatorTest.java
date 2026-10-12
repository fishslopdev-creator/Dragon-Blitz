package dev.playtime.engine.validate;

import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.engine.EngineHarness;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.util.IssueList;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MapValidatorTest {
    @Test
    void builtinContentHasNoErrors() {
        IssueList issues = MapValidator.validate(new EngineHarness().engine);
        assertFalse(issues.hasErrors(), issues.toString());
    }

    @Test
    void reportsBrokenReferences() {
        EngineHarness h = new EngineHarness();
        h.define(ContentKind.CHASES, """
                {"id":"broken_chase","monster":"playtime:nobody","stages":[{"id":"a","success":[{"type":"puzzle_completed","id":"playtime:missing"}],"next":"zzz"}]}
                """);
        h.define(ContentKind.EVENTS, """
                {"id":"self","actions":[{"type":"trigger_event","id":"playtime:self"},{"type":"door_open","id":"playtime:no_door"}]}
                """);
        h.device("trigger", DeviceType.CHASE_TRIGGER, Map.of("chase", "playtime:not_a_chase"));
        String report = MapValidator.validate(h.engine).toString();
        assertTrue(report.contains("unknown monster playtime:nobody"), report);
        assertTrue(report.contains("refers to unknown playtime:missing"), report);
        assertTrue(report.contains("next stage 'zzz' does not exist"), report);
        assertTrue(report.contains("triggers itself with no delay"), report);
        assertTrue(report.contains("door_open refers to unknown playtime:no_door"), report);
        assertTrue(report.contains("chase playtime:not_a_chase does not exist"), report);
        assertTrue(report.contains("has no spawn position"), report);
    }
}

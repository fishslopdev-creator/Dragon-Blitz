package dev.playtime.engine.access;

import dev.playtime.engine.TestContent;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.hand.HandDefinition;
import dev.playtime.engine.hand.HandStats;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.logic.ConditionType;
import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AccessEvaluatorTest {
    private final Map<Id, CredentialDefinition> creds = new HashMap<>();
    private final AccessEvaluator eval;
    private final Map<Id, HandDefinition> hands = new HashMap<>();

    AccessEvaluatorTest() {
        var pack = TestContent.builtins();
        for (CredentialDefinition c : pack.<CredentialDefinition>all(ContentKind.CREDENTIALS)) creds.put(c.id, c);
        for (HandDefinition h : pack.<HandDefinition>all(ContentKind.HANDS)) hands.put(h.id, h.normalize());
        eval = new AccessEvaluator(creds);
    }

    static Facts facts(boolean puzzleDone) {
        return (Facts) Proxy.newProxyInstance(Facts.class.getClassLoader(), new Class<?>[]{Facts.class},
                (proxy, m, args) -> m.getName().equals("puzzleCompleted") && puzzleDone);
    }

    private HandStats stats(String id) {
        return HandStats.of(hands.get(Id.of(id)), Set.of());
    }

    @Test
    void keycardAndClearance() {
        AccessRule rule = new AccessRule();
        rule.credentials.add(Id.of("blue_access_card"));
        assertFalse(eval.evaluate(rule, new AccessRequest(facts(false), Set.of(), Set.of(), null, true)).granted());
        assertTrue(eval.evaluate(rule, new AccessRequest(facts(false), Set.of(Id.of("blue_access_card")), Set.of(), null, true)).granted());

        AccessRule level = new AccessRule();
        level.minClearance = 2;
        var denied = eval.evaluate(level, new AccessRequest(facts(false), Set.of(Id.of("blue_access_card")), Set.of(), null, true));
        assertFalse(denied.granted());
        assertTrue(denied.message().contains("clearance 2"));
        assertTrue(eval.evaluate(level, new AccessRequest(facts(false), Set.of(Id.of("high_security_card")), Set.of(), null, true)).granted());
    }

    @Test
    void consumedCardIsReported() {
        AccessRule rule = new AccessRule();
        rule.credentials.add(Id.of("maintenance_key"));
        rule.consumeCredential = true;
        var r = eval.evaluate(rule, new AccessRequest(facts(false), Set.of(Id.of("maintenance_key")), Set.of(), null, true));
        assertEquals(Id.of("maintenance_key"), r.consumedCredential());
    }

    @Test
    void handScannerAcceptsHandOrTag() {
        AccessRule green = new AccessRule();
        green.scanHands.add(Id.of("green_hand"));
        assertFalse(eval.evaluate(green, new AccessRequest(facts(false), Set.of(), Set.of(), stats("red_hand"), true)).granted());
        assertTrue(eval.evaluate(green, new AccessRequest(facts(false), Set.of(), Set.of(), stats("green_hand"), true)).granted());

        AccessRule red = new AccessRule();
        red.scanTags.add(Id.of("red_scanner"));
        assertTrue(eval.evaluate(red, new AccessRequest(facts(false), Set.of(), Set.of(), stats("omni_hand"), true)).granted(),
                "the Omni-Hand works red scanners");
    }

    @Test
    void multiHandPowerAndPuzzleConditions() {
        AccessRule rule = new AccessRule();
        rule.requiredHands.add(Id.of("blue_hand"));
        rule.requiredHands.add(Id.of("green_hand"));
        rule.requiresPower = true;
        rule.conditions.add(Condition.of(ConditionType.PUZZLE_COMPLETED, Id.of("x")));
        var r = eval.evaluate(rule, new AccessRequest(facts(false), Set.of(), Set.of(Id.of("blue_hand")), null, false));
        assertFalse(r.granted());
        assertEquals(3, r.reasons().size(), r.reasons().toString());
        var ok = eval.evaluate(rule, new AccessRequest(facts(true), Set.of(), Set.of(Id.of("blue_hand"), Id.of("green_hand")), null, true));
        assertTrue(ok.granted());
    }

    @Test
    void customDeniedMessageWins() {
        AccessRule rule = new AccessRule();
        rule.credentials.add(Id.of("laboratory_key"));
        rule.deniedMessage = "Staff only.";
        assertEquals("Staff only.", eval.evaluate(rule, new AccessRequest(facts(false), Set.of(), Set.of(), null, true)).message());
    }

    @Test
    void timedDoorsCloseThemselves() {
        Map<Id, DoorState> states = new HashMap<>();
        DoorService doors = new DoorService(states);
        DoorDefinition d = new DoorDefinition();
        d.id = Id.of("timed");
        d.type = DoorType.TIMED;
        doors.register(d);
        assertEquals(200, d.openTicks);
        assertTrue(doors.open(d.id, 0));
        doors.tick(100);
        assertTrue(doors.isOpen(d.id));
        doors.tick(201);
        assertFalse(doors.isOpen(d.id));
        doors.setForcedLock(d.id, true);
        assertFalse(doors.open(d.id, 300), "force-locked doors stay shut");
    }

    @Test
    void conditionsNestAndDepthIsBounded() {
        Condition c = Condition.all(java.util.List.of(Condition.not(Condition.of(ConditionType.PUZZLE_COMPLETED, Id.of("x")))));
        assertTrue(c.test(facts(false)));
        assertFalse(c.test(facts(false) ) && c.test(facts(true)));
        Condition area = new Condition();
        area.type = ConditionType.IN_AREA;
        area.area = new Box(null, 0, 0, 0, 1, 1, 1);
        assertFalse(area.test(facts(false)));
    }
}

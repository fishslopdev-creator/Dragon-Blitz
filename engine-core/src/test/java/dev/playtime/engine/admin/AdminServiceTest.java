package dev.playtime.engine.admin;

import dev.playtime.engine.engine.EngineHarness;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceTest {
    @Test
    void privilegedCommandsNeedOperator() {
        EngineHarness h = new EngineHarness();
        UUID player = h.join("player");
        AdminService admin = new AdminService(h.engine);
        assertFalse(admin.giveHand(player, player, Id.of("omni_hand")).ok());
        assertFalse(admin.resetPuzzle(player, null).ok());
        assertEquals("You need operator permission for this command.", admin.validate(player).lines().get(0));
        assertFalse(h.engine.progression().progress(player).map(p -> p.hasHand(Id.of("omni_hand"))).orElse(false));

        h.game.p(player).op = true;
        assertTrue(admin.giveHand(player, player, Id.of("red_hand")).ok());
        assertTrue(h.engine.progression().progress(player).orElseThrow().hasHand(Id.of("red_hand")));
        assertTrue(admin.inspect(null, player).lines().stream().anyMatch(l -> l.contains("red_hand")), "console may inspect");
    }

    @Test
    void playersMayReopenTheirOwnMenuOnlyWhenAllowed() {
        EngineHarness h = new EngineHarness();
        UUID a = h.join("a");
        UUID b = h.join("b");
        AdminService admin = new AdminService(h.engine);
        h.engine.sessions().menuClosed(a);
        assertTrue(admin.openMenu(a, a).ok());
        assertFalse(admin.openMenu(a, b).ok(), "cannot open someone else's menu");
        h.engine.config().rules.allowMenuCommand = false;
        assertFalse(admin.openMenu(a, a).ok());
    }

    @Test
    void installsTheSampleThroughTheCommandLayer() {
        EngineHarness h = new EngineHarness();
        AdminService admin = new AdminService(h.engine);
        AdminResult r = admin.installSample(null, BlockRef.of(0, 64, 0), false);
        assertTrue(r.ok(), r.lines().toString());
        assertTrue(h.engine.graph().device(Id.parse("playtime:sample/al_generator")).isPresent());
        assertTrue(admin.connections(null, Id.parse("playtime:sample/al_generator")).lines().stream()
                .anyMatch(l -> l.contains("power")));
    }
}

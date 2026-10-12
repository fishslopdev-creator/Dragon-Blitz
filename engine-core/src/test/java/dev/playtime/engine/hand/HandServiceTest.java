package dev.playtime.engine.hand;

import dev.playtime.engine.TestContent;
import dev.playtime.engine.config.WorldConfig;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.progress.PlayerSaveStore;
import dev.playtime.engine.progress.ProgressionService;
import dev.playtime.engine.util.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HandServiceTest {
    private final UUID p = UUID.randomUUID();
    private HandService hands;
    private ProgressionService progression;

    @BeforeEach
    void setUp() {
        HandRegistry registry = new HandRegistry();
        for (HandDefinition h : TestContent.builtins().<HandDefinition>all(ContentKind.HANDS)) registry.register(h);
        progression = new ProgressionService(new WorldConfig(), new PlayerSaveStore(null), null);
        hands = new HandService(registry, progression);
        progression.progressOrCreate(p).grabPack = Id.of("grabpack_2");
    }

    @Test
    void canonicalAndCustomHandsAreRegistered() {
        for (String h : new String[]{"blue_hand", "red_hand", "green_hand", "purple_hand", "flare_hand", "omni_hand",
                "pressure_hand", "conductive_hand", "pulse_hand"}) {
            assertTrue(hands.registry().contains(Id.of(h)), h);
        }
    }

    @Test
    void lockedHandsCannotBeSelected() {
        var r = hands.select(p, LauncherSide.RIGHT, Id.of("green_hand"));
        assertFalse(r.ok());
        assertTrue(r.message().contains("locked"));
    }

    @Test
    void handsMustFitTheirLauncherAndPack() {
        hands.grant(p, Id.of("blue_hand"), false);
        assertFalse(hands.select(p, LauncherSide.RIGHT, Id.of("blue_hand")).ok(), "blue hand is left-only");
        assertTrue(hands.select(p, LauncherSide.LEFT, Id.of("blue_hand")).ok());
        progression.progressOrCreate(p).grabPack = Id.of("grabpack");
        hands.grant(p, Id.of("purple_hand"), false);
        assertFalse(hands.select(p, LauncherSide.RIGHT, Id.of("purple_hand")).ok(), "purple hand needs GrabPack 2.0");
    }

    @Test
    void pickupFollowsUnlockStepsAndIsPermanent() {
        HandPickupConfig pickup = new HandPickupConfig();
        pickup.id = Id.of("pickup_green");
        pickup.hand = Id.of("green_hand");
        var first = hands.collect(p, pickup);
        assertTrue(first.granted());
        assertTrue(first.removePickup());
        assertEquals(Id.of("green_hand"), progression.progress(p).orElseThrow().selectedRightHand, "auto-equipped");
        var second = hands.collect(p, pickup);
        assertFalse(second.granted());
        assertTrue(second.alreadyOwned());
    }

    @Test
    void chapterLimitedPickup() {
        HandPickupConfig pickup = new HandPickupConfig();
        pickup.id = Id.of("pickup_flare");
        pickup.hand = Id.of("flare_hand");
        pickup.mode = PickupMode.CHAPTER_LIMITED;
        pickup.chapter = Id.of("chapter_3");
        progression.progressOrCreate(p).chapter = Id.of("chapter_2");
        assertFalse(hands.collect(p, pickup).granted());
        progression.progressOrCreate(p).chapter = Id.of("chapter_3");
        assertTrue(hands.collect(p, pickup).granted());
    }

    @Test
    void omniHandRequiresRedAndReplacesItInTheWheel() {
        HandPickupConfig omni = new HandPickupConfig();
        omni.id = Id.of("pickup_omni");
        omni.hand = Id.of("omni_hand");
        assertFalse(hands.collect(p, omni).granted(), "needs the Red Hand first");
        hands.grant(p, Id.of("red_hand"), true);
        assertTrue(hands.collect(p, omni).granted());
        var wheel = hands.wheel(p, LauncherSide.RIGHT);
        assertTrue(wheel.stream().noneMatch(w -> w.hand().equals(Id.of("red_hand"))));
        assertTrue(wheel.stream().anyMatch(w -> w.hand().equals(Id.of("omni_hand")) && w.unlocked() && w.selected()));
    }

    @Test
    void wheelShowsLockedHandsWithoutDetails() {
        var wheel = hands.wheel(p, LauncherSide.RIGHT);
        var pulse = wheel.stream().filter(w -> w.hand().equals(Id.of("pulse_hand"))).findFirst().orElseThrow();
        assertFalse(pulse.unlocked());
        assertEquals("???", pulse.description());
        assertTrue(pulse.abilities().isEmpty());
    }

    @Test
    void upgradesApplyFromFlags() {
        HandDefinition def = new HandDefinition();
        def.id = Id.of("test_hand");
        def.range = 10;
        HandUpgrade up = new HandUpgrade();
        up.requiresFlag = Id.of("upgrade/long_cable");
        up.range = 30.0;
        up.addAbilities.add(HandAbilities.PULL);
        def.upgrades.add(up);
        def.normalize();
        assertEquals(10, HandStats.of(def, java.util.Set.of()).range());
        HandStats upgraded = HandStats.of(def, java.util.Set.of(Id.of("upgrade/long_cable")));
        assertEquals(30, upgraded.range());
        assertTrue(upgraded.has(HandAbilities.PULL));
    }
}

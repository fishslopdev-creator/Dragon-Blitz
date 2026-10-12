package dev.playtime.fabric.test;

import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.puzzle.PuzzleStatus;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.fabric.block.ModBlocks;
import dev.playtime.fabric.client.Keys;
import dev.playtime.fabric.client.screen.HandWheelScreen;
import dev.playtime.fabric.client.screen.MainMenuScreen;
import dev.playtime.fabric.entity.PlaytimeMonster;
import dev.playtime.fabric.server.EngineHolder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Function;

/**
 * End-to-end test in the real client: menus on game-mode change, New Game, the sample
 * facility, hand unlock and wheel, firing the Pulse Hand with the key binding, the
 * Assembly Line puzzle, The Playwright, and the Factory Shutdown chase.
 * Screenshots are written to run/clientGameTest/screenshots.
 */
public class PlaytimeClientGameTest implements FabricClientGameTest {
    private static Id s(String path) {
        return Id.parse("playtime:sample/" + path);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            context.takeScreenshot("01_joined");
            var server = world.getServer();

            check(server.computeOnServer(srv -> EngineHolder.engine() != null), "engine should be running");

            // --- Main menu opens when switching to Survival ---------------------------------
            server.runCommand("gamemode creative @a");
            context.waitTicks(60);
            server.runCommand("gamemode survival @a");
            context.waitForScreen(MainMenuScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("02_menu_on_survival_switch");
            context.runOnClient(mc -> ((MainMenuScreen) mc.screen).press("new_game"));
            context.waitFor(mc -> mc.screen == null, 100);
            UUID player = context.computeOnClient(mc -> mc.player.getUUID());
            check(server.computeOnServer(srv -> EngineHolder.engine().progression().progress(player).isPresent()),
                    "New Game should create a save");

            // Dimension change must not reopen the menu.
            server.runCommand("execute in minecraft:the_nether run tp @p 0 120 0");
            context.waitTicks(40);
            server.runCommand("execute in minecraft:overworld run tp @p 0 -59 0");
            context.waitTicks(40);
            check(context.computeOnClient(mc -> !(mc.screen instanceof MainMenuScreen)), "dimension change must not open the menu");

            // --- Install the sample facility --------------------------------------------------
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);
            server.runCommand("execute as @p at @p run playtime sample install");
            context.waitTicks(40);
            BlockRef start = server.computeOnServer(srv -> EngineHolder.engine().graph()
                    .device(s("start")).orElseThrow().pos);
            BlockPos origin = new BlockPos(start.x(), start.y() - 1, start.z());
            check(server.computeOnServer(srv -> srv.overworld().getBlockState(origin.offset(10, 1, 0))
                    .is(ModBlocks.DEVICES.get("power_generator"))), "generator block should be placed");

            // New game in the sample world (Adventure-like Survival start).
            server.runCommand("gamemode survival @a");
            context.waitForScreen(MainMenuScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("03_sample_menu");
            context.runOnClient(mc -> ((MainMenuScreen) mc.screen).press("new_game"));
            context.waitTicks(10);
            context.runOnClient(mc -> {
                if (mc.screen != null && !(mc.screen instanceof MainMenuScreen)) {
                    // Confirmation dialog for overwriting the earlier save.
                    mc.screen.children().stream()
                            .filter(w -> w instanceof net.minecraft.client.gui.components.Button)
                            .map(w -> (net.minecraft.client.gui.components.Button) w)
                            .findFirst().ifPresent(b -> b.onPress(null));
                }
            });
            context.waitFor(mc -> mc.screen == null, 200);
            context.waitTicks(30);
            context.takeScreenshot("04_sample_start");
            PlayerProgress progress = server.computeOnServer(srv -> EngineHolder.engine().progression().progress(player).orElseThrow());
            check(Id.parse("playtime:grabpack_2").equals(progress.grabPack), "visitor profile gives GrabPack 2.0");

            // --- Pulse Hand pickup + hand wheel -----------------------------------------------
            engine(server, e -> {
                e.useDevice(player, s("pulse_pickup"), null);
                return null;
            });
            context.waitTicks(10);
            context.takeScreenshot("05_pulse_hand_unlocked");
            check(server.computeOnServer(srv -> EngineHolder.engine().progression().progress(player).orElseThrow()
                    .hasHand(Id.of("pulse_hand"))), "Pulse Hand unlocked");
            context.getInput().pressKey(Keys.WHEEL);
            context.waitForScreen(HandWheelScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("06_hand_wheel");
            context.setScreen(() -> null);

            // --- Assembly Line ------------------------------------------------------------------
            engine(server, e -> {
                e.useDevice(player, s("core_shelf"), null);
                e.useDevice(player, s("al_generator"), Id.of("power_core"));
                e.useDevice(player, s("al_generator"), null);
                return null;
            });
            context.waitTicks(5);
            check(stage(server) == 2, "generator stages done, got stage " + stage(server));
            // Stand between the receivers and fire the Pulse Hand at the floor with the key binding.
            server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", origin.getX() + 13, origin.getY() + 1,
                    origin.getZ(), origin.getX() + 16, origin.getY() + 1, origin.getZ()));
            context.waitTicks(10);
            context.getInput().pressKey(Keys.FIRE_RIGHT);
            context.waitTicks(4);
            context.takeScreenshot("07_pulse_fired");
            context.waitTicks(10);
            check(server.computeOnServer(srv -> EngineHolder.engine().graph().state(s("al_conveyor")).active),
                    "pulse should wake both receivers and start the line");
            engine(server, e -> {
                e.useDevice(player, s("al_valve_a"), null);
                e.useDevice(player, s("al_valve_b"), null);
                e.useDevice(player, s("al_valve_c"), null);
                return null;
            });
            context.waitTicks(5);
            check(server.computeOnServer(srv -> EngineHolder.engine().puzzles().state(s("assembly_line")).status == PuzzleStatus.COMPLETED),
                    "Assembly Line should be complete");
            check(server.computeOnServer(srv -> srv.overworld().getBlockState(origin.offset(25, 1, 0)).isAir()),
                    "passage door blocks should be removed");
            server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", origin.getX() + 20, origin.getY() + 1,
                    origin.getZ(), origin.getX() + 25, origin.getY() + 2, origin.getZ()));
            context.waitTicks(10);
            context.takeScreenshot("08_passage_open");

            // --- The Playwright encounter -------------------------------------------------------
            context.waitTicks(60);
            check(server.computeOnServer(srv -> !srv.overworld().getEntities(
                    net.minecraft.world.level.entity.EntityTypeTest.forClass(PlaytimeMonster.class), m -> true).isEmpty()),
                    "The Playwright should have spawned");
            server.runCommand("gamemode spectator @a");
            server.runCommand(String.format("tp @p %d %d %d facing %d %d %d", origin.getX() + 27, origin.getY() + 3,
                    origin.getZ() + 2, origin.getX() + 30, origin.getY() + 2, origin.getZ() + 6));
            context.waitTicks(20);
            context.takeScreenshot("09_playwright");
            server.runCommand("gamemode survival @a");

            // --- Factory Shutdown chase ---------------------------------------------------------
            server.runCommand(String.format("tp @p %d %d %d", origin.getX() + 33, origin.getY() + 1, origin.getZ()));
            context.waitTicks(10);
            engine(server, e -> {
                e.useDevice(player, s("shutdown_lever"), null);
                return null;
            });
            context.waitTicks(10);
            context.takeScreenshot("10_factory_shutdown");
            check(server.computeOnServer(srv -> EngineHolder.engine().chases().isActive(s("factory_shutdown"))), "chase should start");
            context.waitTicks(40);
            engine(server, e -> {
                e.useDevice(player, s("backup_breaker"), null);
                e.useDevice(player, s("backup_generator"), null);
                return null;
            });
            context.waitTicks(10);
            check(server.computeOnServer(srv -> EngineHolder.engine().doors().isOpen(s("exit_door"))), "security door should open");
            engine(server, e -> {
                e.interactions().enter(player, s("exit_volume"));
                return null;
            });
            context.waitTicks(10);
            check(server.computeOnServer(srv -> EngineHolder.engine().progression().progress(player).orElseThrow()
                    .completedChases.contains(s("factory_shutdown"))), "chase should be completed");
            context.takeScreenshot("11_escaped");
            context.setScreen(() -> null);
        }
    }

    private static int stage(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        return server.computeOnServer(srv -> EngineHolder.engine().puzzles().state(s("assembly_line")).stage);
    }

    private static <T> T engine(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
                                Function<PlaytimeEngine, T> action) {
        return server.computeOnServer((MinecraftServer srv) -> action.apply(EngineHolder.engine()));
    }

    @SuppressWarnings("unused")
    private static ServerPlayer first(MinecraftServer srv) {
        return srv.getPlayerList().getPlayers().get(0);
    }

    @SuppressWarnings("unused")
    private static boolean noScreen(Minecraft mc) {
        return mc.screen == null;
    }
}

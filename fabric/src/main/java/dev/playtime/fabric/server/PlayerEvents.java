package dev.playtime.fabric.server;

import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.menu.PlayMode;
import dev.playtime.engine.progress.DeathOutcome;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Forwards player lifecycle events to the engine. */
public final class PlayerEvents {
    /** Death outcomes computed just before drops, consumed when the player respawns. */
    private static final Map<UUID, DeathOutcome> DEATHS = new HashMap<>();

    private PlayerEvents() {}

    public static PlayMode mode(GameType type) {
        return switch (type) {
            case SURVIVAL -> PlayMode.SURVIVAL;
            case CREATIVE -> PlayMode.CREATIVE;
            case ADVENTURE -> PlayMode.ADVENTURE;
            case SPECTATOR -> PlayMode.SPECTATOR;
        };
    }

    public static PlayMode mode(ServerPlayer player) {
        return mode(player.gameMode.getGameModeForPlayer());
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlaytimeEngine engine = EngineHolder.engine();
            if (engine == null) return;
            ServerPlayer player = handler.player;
            engine.sessions().onJoin(player.getUUID(), mode(player));
            Sync.sendPlayerState(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlaytimeEngine engine = EngineHolder.engine();
            if (engine != null) engine.sessions().onLeave(handler.player.getUUID());
            DEATHS.remove(handler.player.getUUID());
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (alive) return;
            DeathOutcome outcome = DEATHS.remove(oldPlayer.getUUID());
            if (outcome != null && outcome.keepInventory()) {
                newPlayer.getInventory().replaceWith(oldPlayer.getInventory());
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PlaytimeEngine engine = EngineHolder.engine();
            if (engine == null) return;
            if (!alive) {
                engine.sessions().respawnPoint(newPlayer.getUUID()).ifPresent(r ->
                        EngineHolder.bridge().teleport(newPlayer.getUUID(), r.pos(), r.yaw()));
            }
            engine.sessions().onRespawn(newPlayer.getUUID(), mode(newPlayer), !alive);
            Sync.sendPlayerState(newPlayer);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            PlaytimeEngine engine = EngineHolder.engine();
            if (engine != null) engine.sessions().onDimensionChange(player.getUUID(), mode(player));
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && !DEATHS.containsKey(player.getUUID())) {
                // Death without the drop step (e.g. spectators): still apply the engine's rules.
                onDeath(player);
            }
        });
    }

    /** Called from the drop mixin before items drop. Returns true to keep the inventory. */
    public static boolean onDeath(ServerPlayer player) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null || !engine.config().enabled) return false;
        DeathOutcome outcome = engine.sessions().onDeath(player.getUUID());
        DEATHS.put(player.getUUID(), outcome);
        return outcome.keepInventory();
    }

    /** Called from the game-mode mixin after a successful change. */
    public static void onGameModeChanged(ServerPlayer player, GameType from, GameType to) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine != null && from != to) engine.sessions().onGameModeChange(player.getUUID(), mode(from), mode(to));
    }
}

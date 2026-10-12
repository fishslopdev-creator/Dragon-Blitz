package dev.playtime.fabric;

import dev.playtime.fabric.block.ModBlocks;
import dev.playtime.fabric.entity.ModEntities;
import dev.playtime.fabric.item.ModItems;
import dev.playtime.fabric.net.Payloads;
import dev.playtime.fabric.net.ServerNetworking;
import dev.playtime.fabric.server.EngineHolder;
import dev.playtime.fabric.server.PlayerEvents;
import dev.playtime.fabric.server.PlaytimeCommands;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point: registers content, networking, events and commands. */
public final class PlaytimeMod implements ModInitializer {
    public static final String MOD_ID = "playtime";
    public static final Logger LOG = LoggerFactory.getLogger("Playtime Engine");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModItems.register();
        ModEntities.register();
        Payloads.register();
        ServerNetworking.register();
        EngineHolder.register();
        PlayerEvents.register();
        PlaytimeCommands.register();
        LOG.info("Playtime Engine initialised");
    }
}

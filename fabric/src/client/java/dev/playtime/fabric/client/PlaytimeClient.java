package dev.playtime.fabric.client;

import dev.playtime.fabric.client.render.CableRenderer;
import dev.playtime.fabric.client.render.MonsterRenderer;
import dev.playtime.fabric.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client entry point: screens, HUD, key bindings, renderers. */
public final class PlaytimeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Keys.register();
        Hud.register();
        ClientNet.register();
        CableRenderer.register();
        EntityRendererRegistry.register(ModEntities.MONSTER, MonsterRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Hud.tick();
            if (ClientState.shakeTicks > 0 && mc.player != null) {
                float s = ClientState.shakeStrength;
                mc.player.setYRot(mc.player.getYRot() + (mc.player.getRandom().nextFloat() - 0.5F) * s * 2);
                mc.player.setXRot(mc.player.getXRot() + (mc.player.getRandom().nextFloat() - 0.5F) * s);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());
    }
}

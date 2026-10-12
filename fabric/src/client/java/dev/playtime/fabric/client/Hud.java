package dev.playtime.fabric.client;

import com.google.gson.JsonObject;
import dev.playtime.fabric.PlaytimeMod;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Objective tracker, selected hands, unlock toasts and screen fades. */
public final class Hud {
    private Hud() {}

    public static void register() {
        HudElementRegistry.addLast(PlaytimeMod.id("hud"), Hud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();

        // Objectives (top-left).
        int y = 6;
        String chapter = ClientState.str(ClientState.sync, "chapter");
        if (!chapter.isEmpty()) {
            g.drawString(font, chapter, 6, y, 0xFFE2B05A);
            y += 11;
        }
        for (JsonObject o : ClientState.array(ClientState.sync, "objectives")) {
            if (!"active".equals(ClientState.str(o, "status"))) continue;
            g.drawString(font, "▸ " + ClientState.str(o, "text"), 6, y, 0xFFEDE6D6);
            y += 10;
            if (y > h / 3) break;
        }
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (ClientState.objectiveTicks > 0) {
            long left = ClientState.objectiveTicks - (now - ClientState.objectiveReceivedAt);
            if (left > 0) {
                String timer = String.format("%s  %d:%02d", ClientState.objectiveText, left / 1200, (left / 20) % 60);
                g.drawCenteredString(font, timer, w / 2, 24, left < 200 ? 0xFFFF5555 : 0xFFFFFFFF);
            }
        }

        // Selected hands (bottom-right).
        drawHand(g, ClientState.str(ClientState.sync, "left"), w - 58, h - 30);
        drawHand(g, ClientState.str(ClientState.sync, "right"), w - 30, h - 30);

        // Unlock toast (top-centre).
        if (System.currentTimeMillis() < ClientState.unlockUntil) {
            int tw = Math.max(font.width(ClientState.unlockTitle), font.width(ClientState.unlockDescription)) + 20;
            g.fill(w / 2 - tw / 2, 40, w / 2 + tw / 2, 68, 0xC0101014);
            g.drawCenteredString(font, ClientState.unlockTitle, w / 2, 45, 0xFFE2B05A);
            g.drawCenteredString(font, ClientState.unlockDescription, w / 2, 56, 0xFFD0D0D0);
        }

        // Fade to black.
        if (ClientState.fadeTicks > 0 && ClientState.fadeTotal > 0) {
            float t = ClientState.fadeTicks / (float) ClientState.fadeTotal;
            int alpha = (int) (255 * Math.sin(Math.PI * t));
            g.fill(0, 0, w, h, (alpha << 24));
        }
    }

    private static void drawHand(GuiGraphics g, String hand, int x, int y) {
        if (hand.isEmpty()) return;
        Identifier id = Identifier.tryParse(hand);
        if (id == null) return;
        g.fill(x - 2, y - 2, x + 26, y + 26, 0x80101014);
        g.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/gui/hands/" + id.getPath() + ".png"),
                x, y, 0, 0, 24, 24, 32, 32, 32, 32);
    }

    /** Called every client tick to advance fades. */
    public static void tick() {
        if (ClientState.fadeTicks > 0) ClientState.fadeTicks--;
        if (ClientState.shakeTicks > 0) ClientState.shakeTicks--;
    }
}

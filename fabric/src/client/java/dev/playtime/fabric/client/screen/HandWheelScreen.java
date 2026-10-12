package dev.playtime.fabric.client.screen;

import com.google.gson.JsonObject;
import dev.playtime.fabric.client.ClientSend;
import dev.playtime.fabric.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Radial hand selector. Move the mouse towards a hand (or scroll, or press 1-9) and
 * left-click to equip; right-click or Esc cancels; Tab switches between launchers.
 * Locked hands are shown greyed out. The choice is checked again by the server.
 */
public class HandWheelScreen extends Screen {
    private boolean left;
    private int highlighted = -1;

    public HandWheelScreen() {
        super(Component.literal("GrabPack"));
    }

    private List<JsonObject> entries() {
        return ClientState.array(ClientState.sync, left ? "leftWheel" : "rightWheel");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        List<JsonObject> entries = entries();
        int cx = width / 2;
        int cy = height / 2;
        int radius = Math.min(80, Math.min(width, height) / 3);
        g.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("playtime", "textures/gui/hand_wheel.png"),
                cx - radius - 24, cy - radius - 24, 0, 0, (radius + 24) * 2, (radius + 24) * 2, 128, 128, 128, 128);
        if (entries.isEmpty()) {
            g.drawCenteredString(font, "No hands for this launcher", cx, cy, 0xFFFFFFFF);
            return;
        }
        double dx = mouseX - cx, dy = mouseY - cy;
        if (dx * dx + dy * dy > 20 * 20) highlighted = indexAt(Math.atan2(dy, dx), entries.size());
        for (int i = 0; i < entries.size(); i++) {
            JsonObject e = entries.get(i);
            double a = angle(i, entries.size());
            int x = cx + (int) (Math.cos(a) * radius) - 12;
            int y = cy + (int) (Math.sin(a) * radius) - 12;
            boolean unlocked = e.get("unlocked").getAsBoolean();
            boolean selected = e.get("selected").getAsBoolean();
            int color = e.get("color").getAsInt();
            if (i == highlighted) g.fill(x - 4, y - 4, x + 28, y + 28, (color & 0x00FFFFFF) | 0xA0000000);
            if (selected) g.fill(x - 2, y + 26, x + 26, y + 28, 0xFFE2B05A);
            Identifier icon = icon(e);
            if (icon != null) g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, 24, 24, 32, 32, 32, 32, unlocked ? 0xFFFFFFFF : 0xFF404040);
            g.drawCenteredString(font, String.valueOf(i + 1), x + 12, y - 10, 0xFFA0A0A0);
        }
        if (highlighted >= 0 && highlighted < entries.size()) {
            JsonObject e = entries.get(highlighted);
            boolean unlocked = e.get("unlocked").getAsBoolean();
            g.drawCenteredString(font, unlocked ? ClientState.str(e, "name") : Component.translatable("playtime.wheel.locked").getString(),
                    cx, cy - 10, unlocked ? 0xFFFFFFFF : 0xFF808080);
            if (unlocked) {
                g.drawWordWrap(font, Component.literal(ClientState.str(e, "description")), cx - 70, cy + 2, 140, 0xFFC8C8C8);
            }
        }
        g.drawCenteredString(font, (left ? "Left" : "Right") + " launcher  •  Tab: switch  •  Right-click: cancel",
                cx, cy + radius + 34, 0xFFA0A0A0);
    }

    private static Identifier icon(JsonObject e) {
        String icon = ClientState.str(e, "icon");
        if (icon.isEmpty()) {
            Identifier id = Identifier.tryParse(ClientState.str(e, "hand"));
            return id == null ? null : Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/gui/hands/" + id.getPath() + ".png");
        }
        return Identifier.tryParse(icon);
    }

    private static double angle(int i, int n) {
        return -Math.PI / 2 + i * 2 * Math.PI / n;
    }

    private static int indexAt(double a, int n) {
        double step = 2 * Math.PI / n;
        double rel = a + Math.PI / 2 + step / 2;
        rel = ((rel % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        return (int) (rel / step) % n;
    }

    private void choose(int index) {
        List<JsonObject> entries = entries();
        if (index < 0 || index >= entries.size()) return;
        JsonObject e = entries.get(index);
        if (!e.get("unlocked").getAsBoolean()) return;
        ClientSend.send("select_hand", ClientSend.obj("side", left ? "left" : "right", "hand", ClientState.str(e, "hand")));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            onClose();
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            choose(highlighted);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int n = entries().size();
        if (n > 0) highlighted = Math.floorMod((highlighted < 0 ? 0 : highlighted) + (scrollY > 0 ? -1 : 1), n);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_TAB) {
            left = !left;
            highlighted = -1;
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            choose(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER) {
            choose(highlighted);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return "pause".equals(ClientState.str(ClientState.sync, "wheelMode"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0x60000000);
    }
}

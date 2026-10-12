package dev.playtime.fabric.client.screen;

import com.google.gson.JsonObject;
import dev.playtime.fabric.client.ClientSend;
import dev.playtime.fabric.client.ClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Player Setup Console: lists player profiles. Operators can edit a profile (starting
 * GrabPack, hands, inventory, keys, rules) or assign it; others may only pick one when
 * the world allows self-service.
 */
public class SetupConsoleScreen extends Screen {
    private final JsonObject data;

    public SetupConsoleScreen(JsonObject data) {
        super(Component.literal("Player Setup Console"));
        this.data = data;
    }

    @Override
    protected void init() {
        boolean op = data.has("operator") && data.get("operator").getAsBoolean();
        int y = 40;
        for (JsonObject p : ClientState.array(data, "profiles")) {
            String id = ClientState.str(p, "id");
            String label = ClientState.str(p, "name") + (p.get("default").getAsBoolean() ? " (default)" : "");
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                ClientSend.send("profile_assign", ClientSend.obj("profile", id));
                onClose();
            }).bounds(width / 2 - 150, y, 200, 20).build());
            if (op) {
                addRenderableWidget(Button.builder(Component.literal("Edit"), b ->
                        ClientSend.send("profile_edit", ClientSend.obj("profile", id))).bounds(width / 2 + 56, y, 60, 20).build());
            }
            y += 24;
            if (y > height - 50) break;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title.getString(), width / 2, 14, 0xFFE2B05A);
        g.drawCenteredString(font, "Click a profile to use it for your next New Game.", width / 2, 26, 0xFFA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

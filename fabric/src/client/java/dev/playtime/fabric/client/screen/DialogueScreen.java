package dev.playtime.fabric.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.playtime.fabric.client.ClientSend;
import dev.playtime.fabric.client.ClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One dialogue node: speaker, text and the options the server allowed. */
public class DialogueScreen extends Screen {
    private final JsonObject view;

    public DialogueScreen(JsonObject view) {
        super(Component.literal(ClientState.str(view, "speaker")));
        this.view = view;
    }

    @Override
    protected void init() {
        int y = height - 28;
        var options = view.has("options") ? view.getAsJsonArray("options") : new com.google.gson.JsonArray();
        if (options.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal(view.has("last") && view.get("last").getAsBoolean() ? "Close" : "Continue"),
                    b -> ClientSend.send("dialogue", ClientSend.obj("option", 0))).bounds(width / 2 - 60, y, 120, 20).build());
            return;
        }
        for (int i = options.size() - 1; i >= 0; i--) {
            JsonElement opt = options.get(i);
            int index = i;
            addRenderableWidget(Button.builder(Component.literal(opt.getAsString()),
                    b -> ClientSend.send("dialogue", ClientSend.obj("option", index))).bounds(width / 2 - 140, y, 280, 20).build());
            y -= 24;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int boxTop = height / 2 - 20;
        g.fill(width / 2 - 160, boxTop, width / 2 + 160, boxTop + 70, 0xC0101014);
        g.drawString(font, ClientState.str(view, "speaker"), width / 2 - 152, boxTop + 6, 0xFFE2B05A);
        g.drawWordWrap(font, Component.literal(ClientState.str(view, "text")), width / 2 - 152, boxTop + 20, 304, 0xFFEDE6D6);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

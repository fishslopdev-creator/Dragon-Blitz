package dev.playtime.fabric.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.playtime.fabric.client.ClientSend;
import dev.playtime.fabric.client.ClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Connection Device: choose the link type, delay, optional target id and name. */
public class ConnectionEditorScreen extends Screen {
    private final JsonObject data;
    private final List<String> types = new ArrayList<>();
    private int type;
    private boolean invert;
    private EditBox delay;
    private EditBox target;
    private EditBox name;

    public ConnectionEditorScreen(JsonObject data) {
        super(Component.literal("New link"));
        this.data = data;
        for (JsonElement e : data.getAsJsonArray("types")) types.add(e.getAsString());
        type = Math.max(0, types.indexOf("activate"));
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        addRenderableWidget(Button.builder(Component.literal("Type: " + types.get(type)), b -> {
            type = (type + 1) % types.size();
            b.setMessage(Component.literal("Type: " + types.get(type)));
        }).bounds(x, 50, 200, 20).build());
        delay = new EditBox(font, x + 80, 80, 120, 18, Component.literal("Delay"));
        delay.setValue("0");
        addRenderableWidget(delay);
        target = new EditBox(font, x + 80, 104, 120, 18, Component.literal("Target"));
        addRenderableWidget(target);
        name = new EditBox(font, x + 80, 128, 120, 18, Component.literal("Name"));
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("Invert: off"), b -> {
            invert = !invert;
            b.setMessage(Component.literal("Invert: " + (invert ? "on" : "off")));
        }).bounds(x, 152, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Create link"), b -> create()).bounds(x, 184, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 102, 184, 98, 20).build());
    }

    private void create() {
        int d;
        try {
            d = Integer.parseInt(delay.getValue().trim());
        } catch (NumberFormatException e) {
            d = 0;
        }
        JsonObject o = ClientSend.obj("from", ClientState.str(data, "from"), "to", ClientState.str(data, "to"),
                "type", types.get(type), "delay", d, "target", target.getValue().trim(), "name", name.getValue(), "invert", invert);
        ClientSend.send("connect", o);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int x = width / 2 - 100;
        g.drawCenteredString(font, ClientState.str(data, "from") + "  →  " + ClientState.str(data, "to"), width / 2, 24, 0xFFE2B05A);
        g.drawString(font, "Delay (ticks)", x, 85, 0xFFEDE6D6);
        g.drawString(font, "Target id", x, 109, 0xFFEDE6D6);
        g.drawString(font, "Name", x, 133, 0xFFEDE6D6);
        g.drawCenteredString(font, "Target: the door/chase/event/puzzle id for those link types (optional)", width / 2, 214, 0xFF909090);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

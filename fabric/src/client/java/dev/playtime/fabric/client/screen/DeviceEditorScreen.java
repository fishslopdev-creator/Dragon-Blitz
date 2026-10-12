package dev.playtime.fabric.client.screen;

import com.google.gson.JsonObject;
import dev.playtime.fabric.client.ClientSend;
import dev.playtime.fabric.client.ClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** Edits one engine device: name, AND-inputs, latch and its key=value settings; lists and removes links. */
public class DeviceEditorScreen extends Screen {
    private final JsonObject data;
    private final JsonObject device;
    private EditBox name;
    private EditBox inputs;
    private MultiLineEditBox settings;
    private boolean latch;

    public DeviceEditorScreen(JsonObject data) {
        super(Component.literal("Device"));
        this.data = data;
        this.device = data.getAsJsonObject("device");
        this.latch = device.has("latch") && device.get("latch").getAsBoolean();
    }

    @Override
    protected void init() {
        int x = 20;
        name = new EditBox(font, x + 60, 30, 160, 18, Component.literal("Name"));
        name.setValue(ClientState.str(device, "name"));
        addRenderableWidget(name);
        inputs = new EditBox(font, x + 60, 54, 40, 18, Component.literal("Inputs"));
        inputs.setValue(device.has("inputsRequired") ? device.get("inputsRequired").getAsString() : "1");
        addRenderableWidget(inputs);
        addRenderableWidget(Button.builder(Component.literal("Latch: " + (latch ? "on" : "off")), b -> {
            latch = !latch;
            b.setMessage(Component.literal("Latch: " + (latch ? "on" : "off")));
        }).bounds(x + 110, 53, 110, 20).build());
        StringBuilder sb = new StringBuilder();
        if (device.has("settings")) {
            for (Map.Entry<String, com.google.gson.JsonElement> e : device.getAsJsonObject("settings").entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue().getAsString()).append('\n');
            }
        }
        settings = MultiLineEditBox.builder().setX(x).setY(96).setShowBackground(true)
                .build(font, 220, height - 140, Component.literal("Settings"));
        settings.setValue(sb.toString());
        addRenderableWidget(settings);
        int ly = 30;
        for (JsonObject link : ClientState.array(data, "links")) {
            String id = ClientState.str(link, "id");
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                ClientSend.send("link_remove", ClientSend.obj("id", id));
                b.active = false;
            }).bounds(width - 30, ly - 4, 16, 16).build());
            ly += 18;
            if (ly > height - 50) break;
        }
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(width / 2 - 104, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, height - 30, 100, 20).build());
    }

    private void save() {
        JsonObject o = new JsonObject();
        o.addProperty("id", ClientState.str(device, "id"));
        o.addProperty("name", name.getValue());
        int n;
        try {
            n = Integer.parseInt(inputs.getValue().trim());
        } catch (NumberFormatException e) {
            n = 1;
        }
        o.addProperty("inputsRequired", n);
        o.addProperty("latch", latch);
        o.addProperty("settings", settings.getValue());
        ClientSend.send("device_save", o);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawString(font, ClientState.str(device, "id") + "  (" + ClientState.str(device, "type") + ")", 20, 12, 0xFFE2B05A);
        g.drawString(font, "Name", 20, 35, 0xFFEDE6D6);
        g.drawString(font, "Inputs", 20, 59, 0xFFEDE6D6);
        g.drawString(font, "Settings (key=value per line)", 20, 84, 0xFFEDE6D6);
        JsonObject state = data.has("state") ? data.getAsJsonObject("state") : new JsonObject();
        g.drawString(font, "active=" + ClientState.str(state, "active") + " powered=" + ClientState.str(state, "powered"),
                250, 12, 0xFFA0A0A0);
        int ly = 30;
        for (JsonObject link : ClientState.array(data, "links")) {
            g.drawString(font, ClientState.str(link, "text"), 250, ly, 0xFFC8C8C8);
            ly += 18;
            if (ly > height - 50) break;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

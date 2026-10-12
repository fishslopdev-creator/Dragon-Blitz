package dev.playtime.fabric.client.screen;

import com.google.gson.JsonObject;
import dev.playtime.fabric.client.ClientSend;
import dev.playtime.fabric.client.ClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Operator editor for world settings and any content definition (doors, puzzles, chases,
 * monsters, profiles...). The server validates and reports problems in chat.
 */
public class JsonEditorScreen extends Screen {
    private final JsonObject request;
    private MultiLineEditBox box;

    public JsonEditorScreen(JsonObject request) {
        super(Component.literal(ClientState.str(request, "title")));
        this.request = request;
    }

    @Override
    protected void init() {
        String text = box != null ? box.getValue() : ClientState.str(request, "json");
        box = MultiLineEditBox.builder().setX(20).setY(30).setShowBackground(true)
                .build(font, width - 40, height - 70, Component.literal("JSON"));
        box.setCharacterLimit(1 << 18);
        box.setValue(text);
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(width / 2 - 104, height - 32, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, height - 32, 100, 20).build());
    }

    private void save() {
        JsonObject o = new JsonObject();
        o.addProperty("mode", ClientState.str(request, "mode"));
        o.addProperty("kind", ClientState.str(request, "kind"));
        o.addProperty("json", box.getValue());
        ClientSend.send("editor_save", o);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title.getString(), width / 2, 12, 0xFFE2B05A);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

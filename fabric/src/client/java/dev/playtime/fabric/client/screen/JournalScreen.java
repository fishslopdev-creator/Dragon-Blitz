package dev.playtime.fabric.client.screen;

import com.google.gson.JsonObject;
import dev.playtime.fabric.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Objectives and collected notes/tapes. Click an entry to read it. */
public class JournalScreen extends Screen {
    private final @Nullable Screen parent;

    public JournalScreen() {
        this(null);
    }

    public JournalScreen(@Nullable Screen parent) {
        super(Component.literal("Journal"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<JsonObject> journal = ClientState.array(ClientState.sync, "journal");
        int y = 40;
        for (JsonObject e : journal) {
            String title = ClientState.str(e, "title");
            String text = ClientState.str(e, "text");
            addRenderableWidget(Button.builder(Component.literal(title), b ->
                    Minecraft.getInstance().setScreen(new TextScreen(this, title, text))).bounds(width / 2 + 10, y, 170, 20).build());
            y += 22;
            if (y > height - 50) break;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, "Journal", width / 2, 14, 0xFFE2B05A);
        g.drawString(font, "Objectives", width / 2 - 180, 28, 0xFFE2B05A);
        g.drawString(font, "Collected", width / 2 + 10, 28, 0xFFE2B05A);
        int y = 42;
        for (JsonObject o : ClientState.array(ClientState.sync, "objectives")) {
            String status = ClientState.str(o, "status");
            String mark = status.equals("completed") ? "✔ " : status.equals("failed") ? "✖ " : "▸ ";
            int color = status.equals("active") ? 0xFFEDE6D6 : 0xFF808080;
            g.drawString(font, mark + ClientState.str(o, "text"), width / 2 - 180, y, color);
            y += 11;
            if (y > height - 40) break;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

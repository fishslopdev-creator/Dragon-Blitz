package dev.playtime.fabric.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Shows a titled block of text: notes, tape transcripts, credits, info panels. */
public class TextScreen extends Screen {
    private final @Nullable Screen parent;
    private final String body;
    private final boolean credits;
    private long opened;

    public TextScreen(@Nullable Screen parent, String title, String body) {
        this(parent, title, body, false);
    }

    public TextScreen(@Nullable Screen parent, String title, String body, boolean credits) {
        super(Component.literal(title));
        this.parent = parent;
        this.body = body == null ? "" : body;
        this.credits = credits;
    }

    @Override
    protected void init() {
        opened = System.currentTimeMillis();
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int w = Math.min(360, width - 40);
        int x = width / 2 - w / 2;
        int y = credits ? (int) (height - (System.currentTimeMillis() - opened) / 40) : 30;
        g.drawCenteredString(font, title.getString(), width / 2, Math.max(y - 16, credits ? y - 16 : 14), 0xFFE2B05A);
        g.drawWordWrap(font, Component.literal(body), x, y, w, 0xFFEDE6D6);
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

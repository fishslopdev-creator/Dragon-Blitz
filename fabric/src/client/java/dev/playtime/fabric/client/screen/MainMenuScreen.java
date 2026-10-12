package dev.playtime.fabric.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.playtime.fabric.client.ClientSend;
import dev.playtime.fabric.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The world's custom main menu. Everything shown comes from the server's MenuPayload:
 * title, subtitle, background, logo, music, button order and visibility, save slots
 * and chapters. Buttons only send intent; the server validates and replies.
 */
public class MainMenuScreen extends Screen {
    private final JsonObject payload;
    private final JsonObject menu;
    private int selectedSlot;
    private boolean showChapters;
    private SoundInstance music;
    private long openedAt;

    public MainMenuScreen(JsonObject payload) {
        super(Component.literal(ClientState.str(payload, "title")));
        this.payload = payload;
        this.menu = payload.has("menu") ? payload.getAsJsonObject("menu") : new JsonObject();
        this.selectedSlot = payload.has("activeSlot") ? payload.get("activeSlot").getAsInt() : 0;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        clearWidgets();
        int x = 40;
        int y = height / 2 - 10;
        if (showChapters) {
            for (JsonObject c : ClientState.array(payload, "chapters")) {
                String id = ClientState.str(c, "id");
                Button b = Button.builder(Component.literal(ClientState.str(c, "name")),
                        btn -> ClientSend.send("menu", ClientSend.obj("action", "chapter", "chapter", id))).bounds(x, y, 160, 20).build();
                b.active = c.has("reached") && c.get("reached").getAsBoolean();
                addRenderableWidget(b);
                y += 24;
            }
            addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
                showChapters = false;
                init();
            }).bounds(x, y + 6, 160, 20).build());
            return;
        }
        for (JsonObject b : buttons()) {
            String type = ClientState.str(b, "type");
            String label = ClientState.str(b, "label");
            if (label.isEmpty()) label = Component.translatable("playtime.menu." + type).getString();
            Button button = Button.builder(Component.literal(label), btn -> press(type)).bounds(x, y, 160, 20).build();
            if (type.equals("new_game")) button.active = !payload.has("mayStartNewGame") || payload.get("mayStartNewGame").getAsBoolean();
            if (type.equals("continue")) button.active = !slotEmpty(selectedSlot);
            if (type.equals("chapters")) button.active = !ClientState.array(payload, "chapters").isEmpty();
            addRenderableWidget(button);
            y += 24;
        }
        // Save slot selector (individual progression with several slots).
        List<JsonObject> slots = ClientState.array(payload, "slots");
        if (slots.size() > 1) {
            int sx = width - 200;
            int sy = height / 2 - 10;
            for (JsonObject s : slots) {
                int index = s.get("index").getAsInt();
                String text = (index == selectedSlot ? "▶ " : "") + "Slot " + (index + 1) + ": "
                        + (s.get("empty").getAsBoolean() ? "Empty" : ClientState.str(s, "chapterName"));
                addRenderableWidget(Button.builder(Component.literal(text), btn -> {
                    selectedSlot = index;
                    init();
                }).bounds(sx, sy, 170, 20).build());
                sy += 24;
            }
        }
    }

    private List<JsonObject> buttons() {
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject b : ClientState.array(menu, "buttons")) {
            if (b.has("visible") && !b.get("visible").getAsBoolean()) continue;
            out.add(b);
        }
        out.sort((a, b) -> Integer.compare(a.has("order") ? a.get("order").getAsInt() : 0, b.has("order") ? b.get("order").getAsInt() : 0));
        return out;
    }

    private boolean slotEmpty(int index) {
        for (JsonObject s : ClientState.array(payload, "slots")) {
            if (s.get("index").getAsInt() == index) return s.get("empty").getAsBoolean();
        }
        return true;
    }

    public void press(String type) {
        if (System.currentTimeMillis() - openedAt < 300) return;
        Minecraft mc = Minecraft.getInstance();
        switch (type) {
            case "continue" -> ClientSend.send("menu", ClientSend.obj("action", "continue", "slot", selectedSlot));
            case "new_game" -> ClientSend.send("menu", ClientSend.obj("action", "new_game", "slot", selectedSlot));
            case "chapters" -> {
                showChapters = true;
                init();
            }
            case "settings" -> mc.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(this, mc.options));
            case "journal" -> mc.setScreen(new JournalScreen(this));
            case "multiplayer_info" -> mc.setScreen(new TextScreen(this, "Multiplayer",
                    ClientState.str(menu, "multiplayerInfo").isEmpty()
                            ? (payload.has("sharedProgression") && payload.get("sharedProgression").getAsBoolean()
                            ? "Everyone on this world shares one save." : "Each player has their own save.")
                            : ClientState.str(menu, "multiplayerInfo")));
            case "quit_to_title" -> {
                stopMusic();
                mc.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
            }
            default -> { }
        }
    }

    /** The server asked to confirm overwriting a save. */
    public void confirmNewGame(String token) {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) ClientSend.send("menu", ClientSend.obj("action", "confirm", "token", token));
            Minecraft.getInstance().setScreen(this);
        }, Component.literal("Start a new game?"), Component.translatable("playtime.menu.confirm_new_game")));
    }

    @Override
    public void added() {
        super.added();
        String musicId = ClientState.str(menu, "music");
        Identifier id = musicId.isEmpty() ? null : Identifier.tryParse(musicId);
        if (id != null && music == null) {
            float volume = menu.has("musicVolume") ? menu.get("musicVolume").getAsFloat() : 0.8F;
            music = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1.0F, volume);
            Minecraft.getInstance().getSoundManager().play(music);
        }
    }

    @Override
    public void removed() {
        super.removed();
    }

    public void stopMusic() {
        if (music != null) {
            Minecraft.getInstance().getSoundManager().stop(music);
            music = null;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        Identifier bg = Identifier.tryParse(ClientState.str(menu, "background"));
        if (bg != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, bg, 0, 0, 0, 0, width, height, 480, 270, 480, 270);
        } else {
            g.fill(0, 0, width, height, 0xFF0C0A0E);
        }
        g.fillGradient(0, 0, width / 2, height, 0xD0000000, 0x30000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int accent = menu.has("accentColor") ? color(menu.get("accentColor")) : 0xFFE23B3B;
        int text = menu.has("textColor") ? color(menu.get("textColor")) : 0xFFEDE6D6;
        float pulse = (float) (0.75 + 0.25 * Math.sin(System.currentTimeMillis() / 600.0));
        Identifier logo = Identifier.tryParse(ClientState.str(menu, "logo"));
        if (logo != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, logo, 40, 30, 0, 0, 200, 60, 200, 60);
        } else {
            g.pose().pushMatrix();
            g.pose().translate(40, 34);
            g.pose().scale(2.5F, 2.5F);
            g.drawString(font, title.getString(), 0, 0, text);
            g.pose().popMatrix();
            g.drawString(font, ClientState.str(payload, "subtitle"), 40, 64, withAlpha(accent, pulse));
        }
        if (!menu.has("showProgressStatus") || menu.get("showProgressStatus").getAsBoolean()) {
            for (JsonObject s : ClientState.array(payload, "slots")) {
                if (s.get("index").getAsInt() != selectedSlot || s.get("empty").getAsBoolean()) continue;
                String status = ClientState.str(s, "chapterName") + "  •  " + ClientState.str(s, "checkpointName")
                        + "  •  " + s.get("handsUnlocked").getAsInt() + " hands  •  "
                        + s.get("collectibles").getAsInt() + " collectibles" + (s.get("completed").getAsBoolean() ? "  •  Completed" : "");
                g.drawString(font, status, 40, height - 24, 0xFFA0A0A0);
            }
        }
    }

    private static int color(JsonElement e) {
        try {
            String s = e.getAsString();
            if (s.startsWith("#")) return (int) Long.parseLong(s.substring(1), 16) | (s.length() == 7 ? 0xFF000000 : 0);
            return (int) Long.parseLong(s);
        } catch (RuntimeException ex) {
            return 0xFFFFFFFF;
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        stopMusic();
        ClientSend.send("menu", ClientSend.obj("action", "close"));
        super.onClose();
    }

    /** Server confirmed the game started or continued. */
    public void closeFromServer() {
        stopMusic();
        Minecraft.getInstance().setScreen(null);
    }
}

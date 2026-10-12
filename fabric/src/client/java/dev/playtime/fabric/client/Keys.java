package dev.playtime.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.playtime.fabric.PlaytimeMod;
import dev.playtime.fabric.client.screen.HandWheelScreen;
import dev.playtime.fabric.client.screen.JournalScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** GrabPack controls, all remappable under Options > Controls > GrabPack. */
public final class Keys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(PlaytimeMod.id("grabpack"));
    public static KeyMapping FIRE_LEFT;
    public static KeyMapping FIRE_RIGHT;
    public static KeyMapping PULL;
    public static KeyMapping RELEASE;
    public static KeyMapping WHEEL;
    public static KeyMapping JOURNAL;

    private Keys() {}

    public static void register() {
        FIRE_LEFT = key("key.playtime.fire_left", GLFW.GLFW_KEY_V);
        FIRE_RIGHT = key("key.playtime.fire_right", GLFW.GLFW_KEY_R);
        PULL = key("key.playtime.pull", GLFW.GLFW_KEY_G);
        RELEASE = key("key.playtime.release", GLFW.GLFW_KEY_X);
        WHEEL = key("key.playtime.hand_wheel", GLFW.GLFW_KEY_LEFT_ALT);
        JOURNAL = key("key.playtime.journal", GLFW.GLFW_KEY_J);
        ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
    }

    private static KeyMapping key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, code, CATEGORY));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        while (FIRE_LEFT.consumeClick()) ClientSend.send("hand", ClientSend.obj("action", "fire", "side", "left"));
        while (FIRE_RIGHT.consumeClick()) ClientSend.send("hand", ClientSend.obj("action", "fire", "side", "right"));
        while (PULL.consumeClick()) {
            ClientSend.send("hand", ClientSend.obj("action", "pull", "side", "right"));
            ClientSend.send("hand", ClientSend.obj("action", "pull", "side", "left"));
        }
        while (RELEASE.consumeClick()) {
            ClientSend.send("hand", ClientSend.obj("action", "release", "side", "right"));
            ClientSend.send("hand", ClientSend.obj("action", "release", "side", "left"));
        }
        while (WHEEL.consumeClick()) {
            if (mc.screen == null && !ClientState.str(ClientState.sync, "grabPack").isEmpty()) mc.setScreen(new HandWheelScreen());
        }
        while (JOURNAL.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new JournalScreen());
        }
    }
}

package dev.playtime.engine.config;

import com.google.gson.annotations.JsonAdapter;
import dev.playtime.engine.util.ColorAdapter;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Appearance and behaviour of a world's custom main menu. */
public class MainMenuConfig {
    public boolean enabled = true;
    /** Overrides the world title on the menu; {@code null} uses {@link WorldConfig#gameTitle}. */
    public String title;
    public String subtitle;
    /** Texture shown behind the menu (a resource-pack or map-supplied texture). */
    public Id background = Id.of("textures/gui/menu/factory_background.png");
    /** Optional logo drawn instead of the title text. */
    public Id logo;
    /** Sound event looped while the menu is open; {@code null} for silence. */
    public Id music = Id.of("menu.factory_hum");
    public float musicVolume = 0.8f;
    public Set<MenuTrigger> showOn = EnumSet.of(MenuTrigger.EVERY_LOGIN, MenuTrigger.SURVIVAL_SWITCH);
    public List<MenuButton> buttons = defaultButtons();
    /** Also treat Adventure mode like Survival (many horror maps run in Adventure). */
    public boolean includeAdventureMode = false;
    public boolean showProgressStatus = true;
    public boolean animated = true;
    /** ARGB accent colour for highlights. */
    @JsonAdapter(ColorAdapter.class)
    public int accentColor = 0xFFE23B3B;
    @JsonAdapter(ColorAdapter.class)
    public int textColor = 0xFFEDE6D6;
    /** Ticks before the player can dismiss the menu (stops accidental double clicks on join). */
    public int inputDelayTicks = 10;
    /** Text shown on the optional multiplayer information panel. */
    public String multiplayerInfo = "";

    public static List<MenuButton> defaultButtons() {
        List<MenuButton> list = new ArrayList<>();
        list.add(new MenuButton(MenuButtonType.CONTINUE, 0));
        list.add(new MenuButton(MenuButtonType.NEW_GAME, 1));
        list.add(new MenuButton(MenuButtonType.CHAPTERS, 2));
        list.add(new MenuButton(MenuButtonType.SETTINGS, 3));
        MenuButton mp = new MenuButton(MenuButtonType.MULTIPLAYER_INFO, 4);
        mp.visible = false;
        list.add(mp);
        return list;
    }

    /** Visible buttons in display order. */
    public List<MenuButton> orderedVisibleButtons() {
        List<MenuButton> out = new ArrayList<>();
        if (buttons != null) {
            for (MenuButton b : buttons) {
                if (b != null && b.visible && b.type != null) out.add(b);
            }
        }
        out.sort(Comparator.comparingInt(b -> b.order));
        return out;
    }
}

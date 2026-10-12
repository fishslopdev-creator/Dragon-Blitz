package dev.playtime.engine.config;

import dev.playtime.engine.util.Id;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-world game configuration, stored as {@code <world>/playtime/world_config.json}.
 * Every field has a safe default so partially written files still load.
 */
public class WorldConfig {
    public static final int SCHEMA_VERSION = 1;

    public int schemaVersion = SCHEMA_VERSION;
    /** When false the engine stays dormant in this world (no menu, no profiles applied). */
    public boolean enabled = true;
    public String gameTitle = "Playtime Factory";
    public String subtitle = "An abandoned toy factory";
    public String chapterName = "Chapter 1";
    public Id startingChapter = Id.of("chapter_1");
    public Id defaultProfile = Id.of("new_player");
    public MainMenuConfig mainMenu = new MainMenuConfig();
    public GameRulesConfig rules = new GameRulesConfig();
    public MultiplayerConfig multiplayer = new MultiplayerConfig();
    public AtmosphereConfig atmosphere = new AtmosphereConfig();
    /** Monster id to enabled flag. Monsters not listed are enabled. */
    public Map<Id, Boolean> monsterToggles = new LinkedHashMap<>();
    /** Number of save slots per player (1 disables slot selection). */
    public int saveSlots = 3;

    public boolean isMonsterEnabled(Id monster) {
        Boolean v = monsterToggles == null ? null : monsterToggles.get(monster);
        return v == null || v;
    }

    public String menuTitle() {
        return mainMenu != null && mainMenu.title != null && !mainMenu.title.isBlank() ? mainMenu.title : gameTitle;
    }

    public String menuSubtitle() {
        return mainMenu != null && mainMenu.subtitle != null ? mainMenu.subtitle : subtitle;
    }

    /** Replaces nulls left by hand-edited JSON with defaults. */
    public WorldConfig normalize() {
        if (mainMenu == null) mainMenu = new MainMenuConfig();
        if (mainMenu.buttons == null) mainMenu.buttons = MainMenuConfig.defaultButtons();
        if (mainMenu.showOn == null) mainMenu.showOn = java.util.EnumSet.noneOf(MenuTrigger.class);
        if (rules == null) rules = new GameRulesConfig();
        if (multiplayer == null) multiplayer = new MultiplayerConfig();
        if (atmosphere == null) atmosphere = new AtmosphereConfig();
        if (monsterToggles == null) monsterToggles = new LinkedHashMap<>();
        if (gameTitle == null) gameTitle = "";
        if (subtitle == null) subtitle = "";
        if (chapterName == null) chapterName = "";
        if (startingChapter == null) startingChapter = Id.of("chapter_1");
        if (defaultProfile == null) defaultProfile = Id.of("new_player");
        saveSlots = Math.max(1, Math.min(10, saveSlots));
        rules.monsterDifficulty = Math.max(0.1, Math.min(5.0, rules.monsterDifficulty));
        return this;
    }
}

package dev.playtime.engine.menu;

import dev.playtime.engine.config.MainMenuConfig;
import dev.playtime.engine.config.MenuTrigger;
import dev.playtime.engine.config.WorldConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides when the custom main menu opens. The game layer reports raw events (join,
 * respawn, dimension change, game-mode change); this class filters duplicates so the
 * menu never opens twice for one transition.
 *
 * <p>Session state is intentionally in memory only: it describes the current login,
 * not saved progress. "First entry" is answered by the caller from the player's save.
 */
public final class MenuTriggerTracker {
    /** Two triggers closer together than this are treated as one transition. */
    public static final long DEDUPE_TICKS = 40;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> bypass = new HashSet<>();

    private static final class Session {
        boolean menuOpen;
        long lastShownTick = Long.MIN_VALUE / 2;
        PlayMode lastMode;
    }

    /** Admin override: skip the menu for this player until cleared (used when testing maps). */
    public void setBypass(UUID player, boolean enabled) {
        if (enabled) bypass.add(player); else bypass.remove(player);
    }

    public boolean isBypassed(UUID player) {
        return bypass.contains(player);
    }

    public MenuDecision onJoin(WorldConfig config, UUID player, PlayMode mode, boolean firstEverEntry, long tick) {
        Session s = sessions.computeIfAbsent(player, k -> new Session());
        s.lastMode = mode;
        s.menuOpen = false;
        MenuDecision gate = gate(config, player, mode);
        if (gate != null) return gate;
        MainMenuConfig menu = config.mainMenu;
        if (firstEverEntry && menu.showOn.contains(MenuTrigger.FIRST_ENTRY)) {
            return show(s, MenuTrigger.FIRST_ENTRY, tick);
        }
        if (menu.showOn.contains(MenuTrigger.EVERY_LOGIN)) {
            return show(s, MenuTrigger.EVERY_LOGIN, tick);
        }
        return MenuDecision.no("join does not trigger the menu in this world");
    }

    /**
     * @param afterDeath false for respawns caused by leaving the End, which must not open the menu
     */
    public MenuDecision onRespawn(WorldConfig config, UUID player, PlayMode mode, boolean afterDeath, long tick) {
        Session s = sessions.computeIfAbsent(player, k -> new Session());
        s.lastMode = mode;
        if (!afterDeath) return MenuDecision.no("respawn was not caused by death");
        MenuDecision gate = gate(config, player, mode);
        if (gate != null) return gate;
        if (!config.mainMenu.showOn.contains(MenuTrigger.AFTER_DEATH)) {
            return MenuDecision.no("after-death menu disabled");
        }
        return show(s, MenuTrigger.AFTER_DEATH, tick);
    }

    /** Dimension travel never opens the menu; it only refreshes the remembered mode. */
    public MenuDecision onDimensionChange(UUID player, PlayMode mode) {
        Session s = sessions.computeIfAbsent(player, k -> new Session());
        s.lastMode = mode;
        return MenuDecision.no("dimension change");
    }

    public MenuDecision onGameModeChange(WorldConfig config, UUID player, PlayMode from, PlayMode to, long tick) {
        Session s = sessions.computeIfAbsent(player, k -> new Session());
        s.lastMode = to;
        if (from == to) return MenuDecision.no("game mode unchanged");
        if (isMenuMode(config, from)) return MenuDecision.no("already in a menu game mode");
        MenuDecision gate = gate(config, player, to);
        if (gate != null) return gate;
        if (!config.mainMenu.showOn.contains(MenuTrigger.SURVIVAL_SWITCH)) {
            return MenuDecision.no("survival-switch menu disabled");
        }
        return show(s, MenuTrigger.SURVIVAL_SWITCH, tick);
    }

    /** Called when the client confirms the menu closed (or the player chose Continue/New Game). */
    public void onMenuClosed(UUID player) {
        Session s = sessions.get(player);
        if (s != null) s.menuOpen = false;
    }

    /** Explicit reopen (command or keybind). Still honours the open-menu dedupe. */
    public MenuDecision requestOpen(WorldConfig config, UUID player, long tick) {
        Session s = sessions.computeIfAbsent(player, k -> new Session());
        if (!config.enabled || config.mainMenu == null || !config.mainMenu.enabled) {
            return MenuDecision.no("menu disabled in this world");
        }
        if (s.menuOpen) return MenuDecision.no("menu already open");
        s.menuOpen = true;
        s.lastShownTick = tick;
        return new MenuDecision(true, null, "requested");
    }

    public void onDisconnect(UUID player) {
        sessions.remove(player);
    }

    public boolean isMenuOpen(UUID player) {
        Session s = sessions.get(player);
        return s != null && s.menuOpen;
    }

    private MenuDecision gate(WorldConfig config, UUID player, PlayMode mode) {
        if (config == null || !config.enabled) return MenuDecision.no("engine disabled in this world");
        if (config.mainMenu == null || !config.mainMenu.enabled) return MenuDecision.no("menu disabled in this world");
        if (bypass.contains(player)) return MenuDecision.no("admin bypass active");
        if (!isMenuMode(config, mode)) return MenuDecision.no("not in survival");
        return null;
    }

    private static boolean isMenuMode(WorldConfig config, PlayMode mode) {
        return mode == PlayMode.SURVIVAL
                || mode == PlayMode.ADVENTURE && config.mainMenu != null && config.mainMenu.includeAdventureMode;
    }

    private MenuDecision show(Session s, MenuTrigger cause, long tick) {
        if (s.menuOpen) return MenuDecision.no("menu already open");
        if (tick - s.lastShownTick < DEDUPE_TICKS) return MenuDecision.no("menu shown moments ago");
        s.menuOpen = true;
        s.lastShownTick = tick;
        return MenuDecision.yes(cause);
    }
}

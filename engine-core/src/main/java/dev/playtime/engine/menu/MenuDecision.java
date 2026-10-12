package dev.playtime.engine.menu;

import dev.playtime.engine.config.MenuTrigger;

/** Result of asking whether to show the main menu. {@code cause} is null when not shown. */
public record MenuDecision(boolean show, MenuTrigger cause, String reason) {
    public static MenuDecision no(String reason) {
        return new MenuDecision(false, null, reason);
    }

    public static MenuDecision yes(MenuTrigger cause) {
        return new MenuDecision(true, cause, "trigger " + cause.name().toLowerCase());
    }
}

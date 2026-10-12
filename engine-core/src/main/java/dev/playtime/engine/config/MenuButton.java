package dev.playtime.engine.config;

/** One button on the custom main menu. Order is ascending; hidden buttons are skipped. */
public class MenuButton {
    public MenuButtonType type = MenuButtonType.CONTINUE;
    /** Custom label; {@code null} uses the translated default for the button type. */
    public String label;
    public boolean visible = true;
    public int order;

    public MenuButton() {}

    public MenuButton(MenuButtonType type, int order) {
        this.type = type;
        this.order = order;
    }
}

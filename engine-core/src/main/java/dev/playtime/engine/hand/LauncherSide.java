package dev.playtime.engine.hand;

public enum LauncherSide {
    LEFT,
    RIGHT,
    /** Hand may be mounted on either launcher. */
    EITHER;

    public boolean accepts(LauncherSide launcher) {
        return this == EITHER || this == launcher;
    }
}

package dev.playtime.engine.config;

/** Map-wide rules. Player profiles may override the death-related values. */
public class GameRulesConfig {
    public boolean keepItemsOnDeath = true;
    public boolean keepHandsOnDeath = true;
    /** Key items and cards picked up since the last checkpoint are lost on death when false. */
    public boolean keepUncheckpointedKeysOnDeath = true;
    public boolean resetPowerOnDeath = false;
    public boolean resetActivePuzzlesOnDeath = true;
    /** Allows hands with the {@code self_pull} ability to drag the player to attach points. */
    public boolean allowSelfPull = false;
    /** Maximum self-pull speed in blocks per tick. */
    public double maxSelfPullSpeed = 0.9;
    public WheelMode wheelMode = WheelMode.LIVE;
    /** Lets non-operators use the Player Setup Console on themselves. Off for real maps. */
    public boolean allowConsoleSelfService = false;
    /** Lets players use /playtime menu to reopen the menu themselves. */
    public boolean allowMenuCommand = true;
    public boolean tutorialsEnabled = true;
    /** Global difficulty multiplier applied to monster speed and perception. */
    public double monsterDifficulty = 1.0;
}

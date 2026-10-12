package dev.playtime.engine.grabpack;

/** Per-hand cable tuning. Values are blocks and ticks. */
public class CableSettings {
    public double maxLength = 24.0;
    public double fireSpeed = 2.5;
    public double retractSpeed = 3.0;
    /** Automatically release after staying attached this long (0 = never). */
    public int maxAttachTicks = 20 * 30;
    /** Release if the player ends up further than {@code maxLength * snapFactor} from the hand. */
    public double snapFactor = 1.15;
    /** Ticks the cable may be obstructed before it snaps back. */
    public int obstructionGraceTicks = 10;
}

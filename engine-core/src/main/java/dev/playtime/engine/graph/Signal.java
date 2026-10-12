package dev.playtime.engine.graph;

public enum Signal {
    ON,
    OFF,
    /** A momentary trigger (button press, pulse) with no "off" afterwards. */
    PULSE
}

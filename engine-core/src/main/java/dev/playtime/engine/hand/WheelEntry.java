package dev.playtime.engine.hand;

import dev.playtime.engine.util.Id;

import java.util.List;

/** Data the client needs to draw one segment of the hand wheel. */
public record WheelEntry(Id hand, String name, String description, Id icon, int color,
                         boolean unlocked, boolean selected, List<String> abilities) {}

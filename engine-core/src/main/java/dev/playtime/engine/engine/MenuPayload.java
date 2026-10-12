package dev.playtime.engine.engine;

import dev.playtime.engine.config.MainMenuConfig;
import dev.playtime.engine.config.MenuTrigger;

import java.util.List;

/**
 * Everything the client needs to draw the main menu. Sent from the server so each
 * world shows its own title, buttons and status.
 */
public record MenuPayload(String title, String subtitle, MainMenuConfig menu, MenuTrigger cause,
                          List<SlotSummary> slots, int activeSlot, boolean sharedProgression,
                          boolean mayStartNewGame, List<ChapterEntry> chapters) {

    public record SlotSummary(int index, boolean empty, String chapterName, String checkpointName,
                              int handsUnlocked, int collectibles, boolean completed, long lastPlayedAt) {}

    public record ChapterEntry(String id, String name, String subtitle, boolean reached) {}
}

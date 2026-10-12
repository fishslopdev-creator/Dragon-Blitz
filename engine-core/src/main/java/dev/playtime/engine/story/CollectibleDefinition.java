package dev.playtime.engine.story;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;

/** Notes, tapes and other collectibles. Collecting adds them to the journal. */
public class CollectibleDefinition {
    public Id id;
    public CollectibleKind kind = CollectibleKind.NOTE;
    public String title;
    /** Body text (notes) or transcript (recordings). */
    public String text = "";
    /** Audio for tapes and logs. */
    public Id audio;
    /** Approximate length so the player can't skip-spam audio. */
    public int durationTicks;
    public Id chapter;
    public List<Action> onCollect = new ArrayList<>();
}

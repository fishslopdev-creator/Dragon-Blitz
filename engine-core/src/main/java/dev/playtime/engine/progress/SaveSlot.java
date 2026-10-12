package dev.playtime.engine.progress;

/** One save slot. A slot with {@code progress == null} is empty. */
public class SaveSlot {
    public String name;
    public long createdAt;
    public long lastPlayedAt;
    public PlayerProgress progress;

    public boolean isEmpty() {
        return progress == null;
    }
}

package dev.playtime.engine.hand;

public enum PickupMode {
    /** Collected once per save; the pickup disappears for that save afterwards. */
    PERMANENT,
    /** Always present; collecting again is harmless (useful for hubs and test rooms). */
    REUSABLE,
    /** Only collectible while the save is in {@link HandPickupConfig#chapter}. */
    CHAPTER_LIMITED,
    /** Unlocks the hand for every player currently in the world. */
    SHARED_ALL
}

package dev.playtime.engine.logic;

/** What starts a story event or chase. */
public enum TriggerType {
    /** Only started by other actions, commands or connections. */
    MANUAL,
    ENTER_AREA,
    ITEM_PICKUP,
    COLLECTIBLE,
    PUZZLE_COMPLETED,
    DOOR_OPENED,
    DEVICE_SIGNAL,
    TIMER,
    DIALOGUE_FINISHED,
    CHAPTER_START,
    HAND_UNLOCKED,
    CHASE_COMPLETED,
    PLAYER_JOIN
}

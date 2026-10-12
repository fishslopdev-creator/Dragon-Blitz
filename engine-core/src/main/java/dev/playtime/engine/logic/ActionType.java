package dev.playtime.engine.logic;

/** Every effect a story event, puzzle reward or chase stage can trigger. */
public enum ActionType {
    // Presentation (handled by the client through the game layer)
    TITLE_CARD,
    SUBTITLE,
    DIALOGUE,
    PLAY_SOUND,
    STOP_SOUND,
    PLAY_RECORDING,
    PARTICLES,
    CAMERA_SHAKE,
    SCREEN_FADE,
    CREDITS,
    // Progress
    SET_FLAG,
    CLEAR_FLAG,
    SET_WORLD_FLAG,
    CLEAR_WORLD_FLAG,
    OBJECTIVE_SET,
    OBJECTIVE_COMPLETE,
    OBJECTIVE_FAIL,
    JOURNAL_ADD,
    GRANT_HAND,
    GIVE_KEY,
    TAKE_KEY,
    GIVE_ITEM,
    SET_CHECKPOINT,
    SET_CHAPTER,
    CHAPTER_COMPLETE,
    GAME_COMPLETE,
    // World
    DOOR_OPEN,
    DOOR_CLOSE,
    DOOR_LOCK,
    DOOR_UNLOCK,
    LIGHTS,
    EMIT_SIGNAL,
    SET_DEVICE,
    RESET_PUZZLE,
    SPAWN_MONSTER,
    DESPAWN_MONSTER,
    START_CHASE,
    STOP_CHASE,
    TRIGGER_EVENT,
    TELEPORT_TO_CHECKPOINT,
    RETURN_TO_MENU
}

package dev.playtime.engine.graph;

/** Every block or marker the engine tracks. One device per placed engine block. */
public enum DeviceType {
    SETUP_CONSOLE,
    DOOR_CONTROLLER,
    KEYCARD_READER,
    HAND_SCANNER,
    ACCESS_TERMINAL,
    SECURITY_TERMINAL,
    GENERATOR,
    FUSE_BOX,
    POWER_NODE,
    POWER_SWITCH,
    POWER_RECEIVER,
    CHARGING_STATION,
    BATTERY_DEVICE,
    CIRCUIT_ENDPOINT,
    PULSE_RECEIVER,
    TIMED_SWITCH,
    BUTTON,
    LEVER,
    MACHINE,
    PART_SOCKET,
    LIGHT,
    ALARM,
    LAUNCH_PAD,
    ATTACH_POINT,
    HAND_PICKUP,
    PUZZLE_CONTROLLER,
    CHASE_TRIGGER,
    CHECKPOINT,
    MONSTER_SPAWN,
    ROUTE_MARKER,
    STORY_TRIGGER,
    OBJECTIVE_TRIGGER,
    CHAPTER_TRANSITION,
    TRIGGER_VOLUME,
    TIMER,
    LOGIC_GATE;

    /** Devices that can carry power along POWER connections. */
    public boolean conductsPower() {
        return switch (this) {
            case GENERATOR, FUSE_BOX, POWER_NODE, POWER_SWITCH, POWER_RECEIVER, CHARGING_STATION, BATTERY_DEVICE,
                 CIRCUIT_ENDPOINT, HAND_SCANNER, SECURITY_TERMINAL, ACCESS_TERMINAL, KEYCARD_READER, DOOR_CONTROLLER,
                 MACHINE, LIGHT, ALARM, PULSE_RECEIVER, LAUNCH_PAD -> true;
            default -> false;
        };
    }
}

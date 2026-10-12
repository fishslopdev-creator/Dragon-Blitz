package dev.playtime.engine.puzzle;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.util.Id;

import java.util.List;
import java.util.UUID;

/** What a template may read and do. Implemented by the {@link PuzzleEngine}. */
public interface PuzzleContext {
    long tick();

    boolean isPowered(Id device);

    boolean isActive(Id device);

    Id socket(Id device, String slot);

    boolean isPuzzleCompleted(Id puzzle);

    /** Switch a device on or off (resetting switches, lighting sequence lamps). */
    void setDeviceActive(Id device, boolean active);

    /** Run presentation or world actions (flash a light, play a tone) for nearby players. */
    void run(List<Action> actions, UUID player);

    /** Shows a short status line to participants (for example "2 / 3 switches"). */
    void progress(String message);
}

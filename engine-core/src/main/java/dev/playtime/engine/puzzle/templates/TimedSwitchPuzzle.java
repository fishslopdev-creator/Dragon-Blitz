package dev.playtime.engine.puzzle.templates;

import com.google.gson.JsonObject;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.puzzle.PuzzleEventType;
import dev.playtime.engine.puzzle.PuzzleLogic;
import dev.playtime.engine.puzzle.PuzzleRun;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * E. Timed switches: activate every switch before the countdown that starts with the first one.
 * <pre>{ "switches": ["playtime:sw_1", "playtime:sw_2", "playtime:sw_3"], "windowTicks": 200 }</pre>
 * On failure the switches are turned off again (with {@code resetDevices}).
 */
public final class TimedSwitchPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("timed_switches");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        return new LinkedHashSet<>(PuzzleRun.ids(s, "switches"));
    }

    @Override
    public boolean ticks(JsonObject settings) { return true; }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        if (e.type() != PuzzleEventType.DEVICE_CHANGED && e.type() != PuzzleEventType.DEVICE_PULSED) return Outcome.CONTINUE;
        List<Id> switches = PuzzleRun.ids(run.settings(), "switches");
        if (e.type() == PuzzleEventType.DEVICE_PULSED) run.put("hit:" + e.device(), "1");
        int active = count(run, switches);
        if (active > 0 && run.getLong("deadline", 0) == 0) {
            run.putLong("deadline", run.ctx().tick() + Math.max(1, PuzzleRun.integer(run.settings(), "windowTicks", 200)));
        }
        if (active >= switches.size()) return Outcome.SUCCESS;
        if (active > 0) {
            long left = Math.max(0, run.getLong("deadline", 0) - run.ctx().tick());
            run.ctx().progress(active + " / " + switches.size() + " switches - " + (left / 20) + "s");
        }
        return Outcome.CONTINUE;
    }

    @Override
    public Outcome tick(PuzzleRun run) {
        long deadline = run.getLong("deadline", 0);
        if (deadline > 0 && run.ctx().tick() > deadline) {
            run.ctx().progress("Too slow!");
            return Outcome.FAILURE;
        }
        return Outcome.CONTINUE;
    }

    private static int count(PuzzleRun run, List<Id> switches) {
        int n = 0;
        for (Id sw : switches) if (run.ctx().isActive(sw) || run.get("hit:" + sw) != null) n++;
        return n;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        if (PuzzleRun.ids(s, "switches").size() < 2) issues.warning(source, "timed_switches works best with two or more switches");
        if (PuzzleRun.integer(s, "windowTicks", 200) <= 0) issues.error(source, "windowTicks must be positive");
    }
}

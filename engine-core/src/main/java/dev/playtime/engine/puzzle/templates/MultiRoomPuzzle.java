package dev.playtime.engine.puzzle.templates;

import com.google.gson.JsonObject;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.puzzle.PuzzleLogic;
import dev.playtime.engine.puzzle.PuzzleRun;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * I. Multiple rooms: several devices across a facility must be active/powered and
 * sub-puzzles complete before the central mechanism unlocks.
 * <pre>{ "active": ["playtime:valve_a", "playtime:valve_b"], "powered": ["playtime:core"],
 *   "puzzles": ["playtime:east_wing", "playtime:west_wing"] }</pre>
 */
public final class MultiRoomPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("multi_room");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        Set<Id> out = new LinkedHashSet<>(PuzzleRun.ids(s, "active"));
        out.addAll(PuzzleRun.ids(s, "powered"));
        out.addAll(PuzzleRun.ids(s, "puzzles"));
        return out;
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        JsonObject s = run.settings();
        List<Id> active = PuzzleRun.ids(s, "active");
        List<Id> powered = PuzzleRun.ids(s, "powered");
        List<Id> puzzles = PuzzleRun.ids(s, "puzzles");
        int done = 0;
        for (Id d : active) if (run.ctx().isActive(d)) done++;
        for (Id d : powered) if (run.ctx().isPowered(d)) done++;
        for (Id p : puzzles) if (run.ctx().isPuzzleCompleted(p)) done++;
        int total = active.size() + powered.size() + puzzles.size();
        if (done >= total) return Outcome.SUCCESS;
        run.ctx().progress(done + " / " + total + " systems online");
        return Outcome.CONTINUE;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        if (devices(s).isEmpty()) issues.error(source, "multi_room needs 'active', 'powered' or 'puzzles'");
    }
}

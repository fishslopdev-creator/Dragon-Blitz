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
 * F. GrabPack traversal: reach a series of markers (trigger volumes, attach points,
 * launch pads) to cross an area.
 * <pre>{ "points": ["playtime:pad_1", "playtime:anchor_2", "playtime:ledge_end"], "ordered": true }</pre>
 */
public final class TraversalPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("traversal");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        return new LinkedHashSet<>(PuzzleRun.ids(s, "points"));
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        boolean reached = e.type() == PuzzleEventType.PLAYER_REACHED || e.type() == PuzzleEventType.DEVICE_PULSED
                || e.type() == PuzzleEventType.DEVICE_CHANGED && e.on();
        if (!reached) return Outcome.CONTINUE;
        List<Id> points = PuzzleRun.ids(run.settings(), "points");
        boolean ordered = PuzzleRun.bool(run.settings(), "ordered", true);
        int idx = run.getInt("next", 0);
        if (ordered) {
            if (idx < points.size() && points.get(idx).equals(e.device())) run.putInt("next", ++idx);
            if (idx >= points.size()) return Outcome.SUCCESS;
        } else {
            run.put("seen:" + e.device(), "1");
            int seen = 0;
            for (Id p : points) if (run.get("seen:" + p) != null) seen++;
            if (seen >= points.size()) return Outcome.SUCCESS;
        }
        return Outcome.CONTINUE;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        if (PuzzleRun.ids(s, "points").isEmpty()) issues.error(source, "traversal needs 'points'");
    }
}

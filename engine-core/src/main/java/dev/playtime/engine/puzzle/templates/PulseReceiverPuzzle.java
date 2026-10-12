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
 * Pulse receivers: wake every listed receiver with the Pulse Hand, optionally within a
 * time window that starts with the first one.
 * <pre>{ "receivers": ["playtime:rx_1", "playtime:rx_2"], "windowTicks": 0, "ordered": false }</pre>
 */
public final class PulseReceiverPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("pulse_receivers");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        return new LinkedHashSet<>(PuzzleRun.ids(s, "receivers"));
    }

    @Override
    public boolean ticks(JsonObject s) {
        return PuzzleRun.integer(s, "windowTicks", 0) > 0;
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        if (e.type() != PuzzleEventType.DEVICE_PULSED) return Outcome.CONTINUE;
        List<Id> receivers = PuzzleRun.ids(run.settings(), "receivers");
        if (!receivers.contains(e.device())) return Outcome.CONTINUE;
        if (PuzzleRun.bool(run.settings(), "ordered", false)) {
            int next = run.getInt("next", 0);
            if (next >= receivers.size() || !receivers.get(next).equals(e.device())) {
                run.ctx().progress("The receivers fall silent. Start again.");
                run.clearData();
                return Outcome.CONTINUE;
            }
            run.putInt("next", next + 1);
        }
        int window = PuzzleRun.integer(run.settings(), "windowTicks", 0);
        if (window > 0 && run.getLong("deadline", 0) == 0) run.putLong("deadline", run.ctx().tick() + window);
        run.put("on:" + e.device(), "1");
        int on = 0;
        for (Id r : receivers) if (run.get("on:" + r) != null) on++;
        if (on >= receivers.size()) return Outcome.SUCCESS;
        run.ctx().progress(on + " / " + receivers.size() + " receivers");
        return Outcome.CONTINUE;
    }

    @Override
    public Outcome tick(PuzzleRun run) {
        long deadline = run.getLong("deadline", 0);
        if (deadline > 0 && run.ctx().tick() > deadline) {
            run.ctx().progress("The receivers powered down.");
            run.clearData();
        }
        return Outcome.CONTINUE;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        if (PuzzleRun.ids(s, "receivers").isEmpty()) issues.error(source, "pulse_receivers needs 'receivers'");
    }
}

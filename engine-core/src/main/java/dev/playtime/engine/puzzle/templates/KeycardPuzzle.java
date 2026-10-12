package dev.playtime.engine.puzzle.templates;

import com.google.gson.JsonObject;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.puzzle.PuzzleEventType;
import dev.playtime.engine.puzzle.PuzzleLogic;
import dev.playtime.engine.puzzle.PuzzleRun;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.util.Set;

/**
 * C. Keycard. Settings:
 * <pre>{ "reader": "playtime:reader_1", "credentials": ["playtime:blue_access_card"], "minClearance": 0,
 *   "requiresPower": true }</pre>
 */
public final class KeycardPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("keycard");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        Id reader = PuzzleRun.id(s, "reader");
        return reader == null ? Set.of() : Set.of(reader);
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        if (e.type() != PuzzleEventType.CARD_SWIPED) return Outcome.CONTINUE;
        JsonObject s = run.settings();
        if (PuzzleRun.bool(s, "requiresPower", false) && !run.ctx().isPowered(e.device())) {
            run.ctx().progress("The reader is dead.");
            return Outcome.CONTINUE;
        }
        Set<Id> cards = PuzzleRun.idSet(s, "credentials");
        int min = PuzzleRun.integer(s, "minClearance", 0);
        boolean ok = e.item() != null && (cards.contains(e.item()) || min > 0 && e.level() >= min);
        if (ok) return Outcome.SUCCESS;
        run.ctx().progress("Access denied.");
        return Outcome.CONTINUE;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        if (PuzzleRun.id(s, "reader") == null) issues.error(source, "keycard needs 'reader'");
        if (PuzzleRun.ids(s, "credentials").isEmpty() && PuzzleRun.integer(s, "minClearance", 0) <= 0) {
            issues.error(source, "keycard needs 'credentials' or 'minClearance'");
        }
    }
}

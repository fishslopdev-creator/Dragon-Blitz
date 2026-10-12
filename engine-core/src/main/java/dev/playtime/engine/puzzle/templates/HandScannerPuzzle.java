package dev.playtime.engine.puzzle.templates;

import com.google.gson.JsonObject;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.puzzle.PuzzleEventType;
import dev.playtime.engine.puzzle.PuzzleLogic;
import dev.playtime.engine.puzzle.PuzzleRun;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IdSets;
import dev.playtime.engine.util.IssueList;

import java.util.Set;

/**
 * H. Hand scanner: fire the right hand at the scanner. Extra requirements (story flags,
 * other puzzles) go in the puzzle's {@code success} conditions.
 * <pre>{ "scanner": "playtime:scanner_lab", "hands": ["playtime:green_hand"], "tags": [], "requiresPower": true }</pre>
 */
public final class HandScannerPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("hand_scanner");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        Id scanner = PuzzleRun.id(s, "scanner");
        return scanner == null ? Set.of() : Set.of(scanner);
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        if (e.type() != PuzzleEventType.HAND_SCANNED) return Outcome.CONTINUE;
        JsonObject s = run.settings();
        if (PuzzleRun.bool(s, "requiresPower", false) && !run.ctx().isPowered(e.device())) {
            run.ctx().progress("The scanner has no power.");
            return Outcome.CONTINUE;
        }
        Set<Id> hands = PuzzleRun.idSet(s, "hands");
        Set<Id> tags = PuzzleRun.idSet(s, "tags");
        boolean ok = hands.contains(e.item()) || !tags.isEmpty() && IdSets.containsAny(e.tags(), tags);
        if (ok) return Outcome.SUCCESS;
        run.ctx().progress("Scanner rejected that hand.");
        return Outcome.CONTINUE;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        if (PuzzleRun.id(s, "scanner") == null) issues.error(source, "hand_scanner needs 'scanner'");
        if (PuzzleRun.ids(s, "hands").isEmpty() && PuzzleRun.ids(s, "tags").isEmpty()) {
            issues.error(source, "hand_scanner needs 'hands' or 'tags'");
        }
    }
}

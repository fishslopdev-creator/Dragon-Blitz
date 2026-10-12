package dev.playtime.engine.puzzle.templates;

import com.google.gson.JsonObject;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.puzzle.PuzzleEventType;
import dev.playtime.engine.puzzle.PuzzleLogic;
import dev.playtime.engine.puzzle.PuzzleRun;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.util.Map;
import java.util.Set;

/**
 * B. Fuse collection. Settings:
 * <pre>{ "fuseBox": "playtime:fuse_box_1",
 *   "slots": { "a": "playtime:fuse", "b": "playtime:fuse", "c": "playtime:fuse" } }</pre>
 */
public final class FuseCollectionPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("fuse_collection");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        Id box = PuzzleRun.id(s, "fuseBox");
        return box == null ? Set.of() : Set.of(box);
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        if (e.type() != PuzzleEventType.SOCKET_CHANGED) return Outcome.CONTINUE;
        Id box = PuzzleRun.id(run.settings(), "fuseBox");
        Map<String, Id> slots = PuzzleRun.idMap(run.settings(), "slots");
        int filled = 0;
        for (Map.Entry<String, Id> slot : slots.entrySet()) {
            if (slot.getValue().equals(run.ctx().socket(box, slot.getKey()))) filled++;
        }
        if (filled >= slots.size()) return Outcome.SUCCESS;
        run.ctx().progress(filled + " / " + slots.size() + " fuses");
        return Outcome.CONTINUE;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        if (PuzzleRun.id(s, "fuseBox") == null) issues.error(source, "fuse_collection needs 'fuseBox'");
        if (PuzzleRun.idMap(s, "slots").isEmpty()) issues.error(source, "fuse_collection needs at least one slot");
    }
}

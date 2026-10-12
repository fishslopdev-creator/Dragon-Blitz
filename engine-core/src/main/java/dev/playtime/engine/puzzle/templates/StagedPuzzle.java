package dev.playtime.engine.puzzle.templates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.playtime.engine.logic.Action;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.puzzle.PuzzleLogic;
import dev.playtime.engine.puzzle.PuzzleRun;
import dev.playtime.engine.puzzle.PuzzleTemplates;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;
import dev.playtime.engine.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * J. Staged puzzle: runs other templates one after another, each with an optional time
 * limit and enter/complete actions. This is how multi-step set pieces like the sample
 * Assembly Line are built from the same parts map creators use.
 * <pre>{
 *   "stages": [
 *     { "name": "Restore the generator", "template": "playtime:fuse_collection", "settings": { ... },
 *       "timeLimitTicks": 0, "failsPuzzle": false, "onEnter": [actions], "onComplete": [actions] },
 *     ...
 *   ]
 * }</pre>
 * A failed stage restarts itself unless {@code failsPuzzle} is true.
 */
public final class StagedPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("staged");

    private final PuzzleTemplates templates;

    public StagedPuzzle(PuzzleTemplates templates) {
        this.templates = templates;
    }

    @Override
    public Id id() { return ID; }

    private static List<JsonObject> stages(JsonObject s) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement e = s.get("stages");
        if (e instanceof JsonArray arr) for (JsonElement x : arr) if (x instanceof JsonObject o) out.add(o);
        return out;
    }

    private PuzzleLogic logic(JsonObject stage) {
        Id template = PuzzleRun.id(stage, "template");
        return template == null || template.equals(ID) ? null : templates.get(template).orElse(null);
    }

    private static JsonObject settings(JsonObject stage) {
        return stage.get("settings") instanceof JsonObject o ? o : new JsonObject();
    }

    @Override
    public Set<Id> devices(JsonObject s) {
        Set<Id> out = new LinkedHashSet<>();
        for (JsonObject stage : stages(s)) {
            PuzzleLogic l = logic(stage);
            if (l != null) out.addAll(l.devices(settings(stage)));
        }
        return out;
    }

    @Override
    public boolean ticks(JsonObject s) {
        return true;
    }

    @Override
    public void start(PuzzleRun run) {
        enter(run, 0);
    }

    private void enter(PuzzleRun run, int index) {
        List<JsonObject> stages = stages(run.settings());
        run.state().stage = index;
        if (index >= stages.size()) return;
        JsonObject stage = stages.get(index);
        int limit = PuzzleRun.integer(stage, "timeLimitTicks", 0);
        run.putLong("stage_deadline", limit > 0 ? run.ctx().tick() + limit : 0);
        String name = stage.has("name") ? stage.get("name").getAsString() : "Stage " + (index + 1);
        run.ctx().progress(name);
        run.ctx().run(actions(stage, "onEnter"), null);
        PuzzleLogic logic = logic(stage);
        if (logic != null) {
            PuzzleRun sub = sub(run, stage, index);
            logic.start(sub);
        }
    }

    private static PuzzleRun sub(PuzzleRun run, JsonObject stage, int index) {
        return run.nested(settings(stage), "s" + index + ".");
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        List<JsonObject> stages = stages(run.settings());
        int index = run.state().stage;
        if (index >= stages.size()) return Outcome.SUCCESS;
        JsonObject stage = stages.get(index);
        PuzzleLogic logic = logic(stage);
        if (logic == null) return Outcome.CONTINUE;
        Outcome o = logic.onEvent(sub(run, stage, index), e);
        return handle(run, stages, index, o, e);
    }

    @Override
    public Outcome tick(PuzzleRun run) {
        List<JsonObject> stages = stages(run.settings());
        int index = run.state().stage;
        if (index >= stages.size()) return Outcome.SUCCESS;
        JsonObject stage = stages.get(index);
        long deadline = run.getLong("stage_deadline", 0);
        if (deadline > 0 && run.ctx().tick() > deadline) {
            run.ctx().progress("Out of time!");
            return handle(run, stages, index, Outcome.FAILURE, null);
        }
        PuzzleLogic logic = logic(stage);
        if (logic != null && logic.ticks(settings(stage))) {
            return handle(run, stages, index, logic.tick(sub(run, stage, index)), null);
        }
        return Outcome.CONTINUE;
    }

    private Outcome handle(PuzzleRun run, List<JsonObject> stages, int index, Outcome o, PuzzleEvent cause) {
        JsonObject stage = stages.get(index);
        if (o == Outcome.SUCCESS) {
            run.ctx().run(actions(stage, "onComplete"), cause == null ? null : cause.player());
            if (index + 1 >= stages.size()) {
                run.state().stage = stages.size();
                return Outcome.SUCCESS;
            }
            enter(run, index + 1);
            return Outcome.CONTINUE;
        }
        if (o == Outcome.FAILURE) {
            if (PuzzleRun.bool(stage, "failsPuzzle", false)) return Outcome.FAILURE;
            run.ctx().run(actions(stage, "onFail"), null);
            sub(run, stage, index).clearData();
            enter(run, index);
        }
        return Outcome.CONTINUE;
    }

    private static List<Action> actions(JsonObject stage, String key) {
        List<Action> out = new ArrayList<>();
        if (stage.get(key) instanceof JsonArray arr) {
            for (JsonElement e : arr) {
                Action a = Json.fromJson(e, Action.class);
                if (a != null && a.type != null) out.add(a);
            }
        }
        return out;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        List<JsonObject> stages = stages(s);
        if (stages.isEmpty()) issues.error(source, "staged puzzle needs at least one stage");
        for (int i = 0; i < stages.size(); i++) {
            JsonObject stage = stages.get(i);
            Id template = PuzzleRun.id(stage, "template");
            if (template == null) {
                issues.error(source, "stage " + (i + 1) + " has no template");
            } else if (template.equals(ID)) {
                issues.error(source, "stage " + (i + 1) + " cannot nest another staged puzzle");
            } else {
                PuzzleLogic l = templates.get(template).orElse(null);
                if (l == null) issues.error(source, "stage " + (i + 1) + " uses unknown template " + template);
                else l.validate(settings(stage), issues, source + " stage " + (i + 1));
            }
        }
    }
}

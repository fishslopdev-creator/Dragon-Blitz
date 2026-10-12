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
 * A. Power routing. Settings:
 * <pre>{
 *   "receivers": ["playtime:door_receiver"],   // must all be powered
 *   "nodes": ["playtime:node_1", "playtime:node_2"],
 *   "minActiveNodes": 2,                        // how many nodes must be active
 *   "order": ["playtime:node_1", "playtime:node_2"], // optional activation order
 *   "failOnWrongOrder": false                  // otherwise a wrong node resets the order
 * }</pre>
 */
public final class PowerRoutingPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("power_routing");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        Set<Id> out = new LinkedHashSet<>(PuzzleRun.ids(s, "receivers"));
        out.addAll(PuzzleRun.ids(s, "nodes"));
        return out;
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        JsonObject s = run.settings();
        List<Id> order = PuzzleRun.ids(s, "order");
        if (!order.isEmpty() && e.type() == PuzzleEventType.DEVICE_CHANGED && e.on() && order.contains(e.device())) {
            int idx = run.getInt("order_index", 0);
            if (idx < order.size() && order.get(idx).equals(e.device())) {
                run.putInt("order_index", idx + 1);
            } else {
                if (PuzzleRun.bool(s, "failOnWrongOrder", false)) return Outcome.FAILURE;
                for (Id node : order) run.ctx().setDeviceActive(node, false);
                run.putInt("order_index", 0);
                run.ctx().progress("The circuit sparks and resets.");
                return Outcome.CONTINUE;
            }
        }
        return check(run, order);
    }

    private Outcome check(PuzzleRun run, List<Id> order) {
        JsonObject s = run.settings();
        List<Id> nodes = PuzzleRun.ids(s, "nodes");
        int active = 0;
        for (Id n : nodes) if (run.ctx().isActive(n) || run.ctx().isPowered(n)) active++;
        int needed = PuzzleRun.integer(s, "minActiveNodes", nodes.size());
        boolean receiversOk = true;
        for (Id r : PuzzleRun.ids(s, "receivers")) receiversOk &= run.ctx().isPowered(r);
        boolean orderOk = order.isEmpty() || run.getInt("order_index", 0) >= order.size();
        if (receiversOk && active >= needed && orderOk) return Outcome.SUCCESS;
        if (!nodes.isEmpty()) run.ctx().progress(active + " / " + needed + " nodes active");
        return Outcome.CONTINUE;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        if (PuzzleRun.ids(s, "receivers").isEmpty() && PuzzleRun.ids(s, "nodes").isEmpty()) {
            issues.error(source, "power_routing needs 'receivers' and/or 'nodes'");
        }
        if (PuzzleRun.integer(s, "minActiveNodes", 0) > PuzzleRun.ids(s, "nodes").size()) {
            issues.error(source, "minActiveNodes is larger than the number of nodes");
        }
    }
}

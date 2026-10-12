package dev.playtime.engine.puzzle.templates;

import com.google.gson.JsonObject;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.puzzle.PuzzleEventType;
import dev.playtime.engine.puzzle.PuzzleLogic;
import dev.playtime.engine.puzzle.PuzzleRun;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * G. Machine assembly: insert parts into sockets, then operate the machine.
 * <pre>{
 *   "parts": { "playtime:socket_gear#main": "playtime:gear", "playtime:socket_belt#main": "playtime:belt" },
 *   "activator": "playtime:start_lever",
 *   "machine": "playtime:press",        // optional; must be powered when requiresPower
 *   "requiresPower": true
 * }</pre>
 * Socket keys are {@code device#slot}.
 */
public final class MachineAssemblyPuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("machine_assembly");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        Set<Id> out = new LinkedHashSet<>();
        for (String key : PuzzleRun.idMap(s, "parts").keySet()) {
            Id dev = Id.tryParse(key.contains("#") ? key.substring(0, key.indexOf('#')) : key);
            if (dev != null) out.add(dev);
        }
        Id act = PuzzleRun.id(s, "activator");
        if (act != null) out.add(act);
        Id machine = PuzzleRun.id(s, "machine");
        if (machine != null) out.add(machine);
        return out;
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        JsonObject s = run.settings();
        int[] counts = partCounts(run, PuzzleRun.idMap(s, "parts"));
        if (e.type() == PuzzleEventType.SOCKET_CHANGED) {
            run.ctx().progress(counts[0] + " / " + counts[1] + " parts installed");
            return Outcome.CONTINUE;
        }
        Id activator = PuzzleRun.id(s, "activator");
        boolean activated = e.device().equals(activator) && (e.type() == PuzzleEventType.DEVICE_PULSED
                || e.type() == PuzzleEventType.DEVICE_CHANGED && e.on());
        if (!activated) return Outcome.CONTINUE;
        if (counts[0] < counts[1]) {
            run.ctx().progress("The machine grinds. Parts are missing.");
            run.ctx().setDeviceActive(activator, false);
            return Outcome.CONTINUE;
        }
        Id machine = PuzzleRun.id(s, "machine");
        if (PuzzleRun.bool(s, "requiresPower", false) && machine != null && !run.ctx().isPowered(machine)) {
            run.ctx().progress("There's no power to the machine.");
            run.ctx().setDeviceActive(activator, false);
            return Outcome.CONTINUE;
        }
        return Outcome.SUCCESS;
    }

    private static int[] partCounts(PuzzleRun run, Map<String, Id> parts) {
        int ok = 0;
        for (Map.Entry<String, Id> p : parts.entrySet()) {
            String key = p.getKey();
            int hash = key.indexOf('#');
            Id dev = Id.tryParse(hash < 0 ? key : key.substring(0, hash));
            String slot = hash < 0 ? "main" : key.substring(hash + 1);
            if (dev != null && p.getValue().equals(run.ctx().socket(dev, slot))) ok++;
        }
        return new int[]{ok, parts.size()};
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        if (PuzzleRun.idMap(s, "parts").isEmpty()) issues.error(source, "machine_assembly needs 'parts'");
        if (PuzzleRun.id(s, "activator") == null) issues.error(source, "machine_assembly needs an 'activator'");
    }
}

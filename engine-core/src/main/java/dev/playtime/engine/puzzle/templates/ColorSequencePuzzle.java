package dev.playtime.engine.puzzle.templates;

import com.google.gson.JsonObject;
import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.ActionType;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.puzzle.PuzzleEventType;
import dev.playtime.engine.puzzle.PuzzleLogic;
import dev.playtime.engine.puzzle.PuzzleRun;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * D. Colour sequence (Simon-style). The puzzle lights lamps and plays tones, then the
 * player repeats the order on buttons. Settings:
 * <pre>{
 *   "buttons": ["playtime:btn_red", "playtime:btn_blue", "playtime:btn_green"],
 *   "lights":  { "playtime:btn_red": "playtime:lamp_red", ... },   // optional
 *   "tones":   { "playtime:btn_red": "playtime:tone.low", ... },    // optional sound per button
 *   "sequence": ["playtime:btn_red", "playtime:btn_green"],         // fixed, or:
 *   "length": 5,                                                     // random sequence length
 *   "stepTicks": 16
 * }</pre>
 */
public final class ColorSequencePuzzle implements PuzzleLogic {
    public static final Id ID = Id.of("color_sequence");

    @Override
    public Id id() { return ID; }

    @Override
    public Set<Id> devices(JsonObject s) {
        return new LinkedHashSet<>(PuzzleRun.ids(s, "buttons"));
    }

    @Override
    public void start(PuzzleRun run) {
        JsonObject s = run.settings();
        List<Id> sequence = PuzzleRun.ids(s, "sequence");
        if (sequence.isEmpty()) {
            List<Id> buttons = PuzzleRun.ids(s, "buttons");
            int length = Math.max(1, PuzzleRun.integer(s, "length", 4));
            Random random = new Random(run.ctx().tick() * 31 + run.state().attempts);
            for (int i = 0; i < length && !buttons.isEmpty(); i++) sequence.add(buttons.get(random.nextInt(buttons.size())));
        }
        run.putIds("sequence", sequence);
        run.putInt("input", 0);
        play(run, sequence);
    }

    private void play(PuzzleRun run, List<Id> sequence) {
        JsonObject s = run.settings();
        int step = Math.max(4, PuzzleRun.integer(s, "stepTicks", 16));
        Map<String, Id> lights = PuzzleRun.idMap(s, "lights");
        Map<String, Id> tones = PuzzleRun.idMap(s, "tones");
        List<Action> show = new ArrayList<>();
        int delay = step;
        for (Id button : sequence) {
            Id lamp = lights.get(button.toString());
            if (lamp != null) {
                show.add(timed(ActionType.SET_DEVICE, lamp, true, delay));
                show.add(timed(ActionType.SET_DEVICE, lamp, false, delay + step / 2));
            }
            Id tone = tones.get(button.toString());
            if (tone != null) {
                Action a = timed(ActionType.PLAY_SOUND, null, true, delay);
                a.sound = tone;
                show.add(a);
            }
            delay += step;
        }
        run.putLong("listen_after", run.ctx().tick() + delay);
        run.ctx().run(show, null);
    }

    private static Action timed(ActionType type, Id id, boolean on, int delay) {
        Action a = Action.of(type, id);
        a.on = on;
        a.delay = delay;
        return a;
    }

    @Override
    public Outcome onEvent(PuzzleRun run, PuzzleEvent e) {
        boolean press = e.type() == PuzzleEventType.DEVICE_PULSED
                || e.type() == PuzzleEventType.DEVICE_CHANGED && e.on();
        if (!press) return Outcome.CONTINUE;
        if (run.ctx().tick() < run.getLong("listen_after", 0)) {
            run.ctx().progress("Watch the lights first.");
            return Outcome.CONTINUE;
        }
        List<Id> sequence = run.getIds("sequence");
        int input = run.getInt("input", 0);
        if (input >= sequence.size()) return Outcome.SUCCESS;
        if (!sequence.get(input).equals(e.device())) return Outcome.FAILURE;
        input++;
        run.putInt("input", input);
        if (input >= sequence.size()) return Outcome.SUCCESS;
        run.ctx().progress(input + " / " + sequence.size());
        return Outcome.CONTINUE;
    }

    @Override
    public void validate(JsonObject s, IssueList issues, String source) {
        List<Id> buttons = PuzzleRun.ids(s, "buttons");
        if (buttons.isEmpty()) issues.error(source, "color_sequence needs 'buttons'");
        for (Id step : PuzzleRun.ids(s, "sequence")) {
            if (!buttons.contains(step)) issues.error(source, "sequence step " + step + " is not one of the buttons");
        }
    }
}

package dev.playtime.engine.puzzle;

import dev.playtime.engine.puzzle.templates.ColorSequencePuzzle;
import dev.playtime.engine.puzzle.templates.FuseCollectionPuzzle;
import dev.playtime.engine.puzzle.templates.HandScannerPuzzle;
import dev.playtime.engine.puzzle.templates.KeycardPuzzle;
import dev.playtime.engine.puzzle.templates.MachineAssemblyPuzzle;
import dev.playtime.engine.puzzle.templates.MultiRoomPuzzle;
import dev.playtime.engine.puzzle.templates.PowerRoutingPuzzle;
import dev.playtime.engine.puzzle.templates.PulseReceiverPuzzle;
import dev.playtime.engine.puzzle.templates.StagedPuzzle;
import dev.playtime.engine.puzzle.templates.TimedSwitchPuzzle;
import dev.playtime.engine.puzzle.templates.TraversalPuzzle;
import dev.playtime.engine.util.Id;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Registry of puzzle templates. Addons call {@link #register} during startup. */
public final class PuzzleTemplates {
    private final Map<Id, PuzzleLogic> templates = new LinkedHashMap<>();

    public static PuzzleTemplates withBuiltins() {
        PuzzleTemplates t = new PuzzleTemplates();
        t.register(new PowerRoutingPuzzle());
        t.register(new FuseCollectionPuzzle());
        t.register(new KeycardPuzzle());
        t.register(new ColorSequencePuzzle());
        t.register(new TimedSwitchPuzzle());
        t.register(new TraversalPuzzle());
        t.register(new MachineAssemblyPuzzle());
        t.register(new HandScannerPuzzle());
        t.register(new MultiRoomPuzzle());
        t.register(new PulseReceiverPuzzle());
        t.register(new StagedPuzzle(t));
        return t;
    }

    public void register(PuzzleLogic logic) {
        templates.put(logic.id(), logic);
    }

    public Optional<PuzzleLogic> get(Id id) {
        return Optional.ofNullable(id == null ? null : templates.get(id));
    }

    public Collection<PuzzleLogic> all() {
        return Collections.unmodifiableCollection(templates.values());
    }
}

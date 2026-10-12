package dev.playtime.engine.story;

import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Server-side dialogue state. Clients only send the index of the option they picked;
 * conditions are re-checked here so hidden options cannot be chosen by a modified client.
 */
public final class DialogueSession {
    private final Map<UUID, Active> sessions = new HashMap<>();
    private final Function<Id, DialogueTree> trees;
    private final Function<UUID, Facts> facts;
    private final BiConsumer<List<Action>, UUID> runner;

    private record Active(DialogueTree tree, String node) {}

    /** What the dialogue screen shows. {@code options} are only the ones the player may pick. */
    public record View(Id dialogue, String speaker, Id portrait, String text, Id voice, List<String> options, boolean last) {}

    public DialogueSession(Function<Id, DialogueTree> trees, Function<UUID, Facts> facts,
                           BiConsumer<List<Action>, UUID> runner) {
        this.trees = trees;
        this.facts = facts;
        this.runner = runner;
    }

    public View start(UUID player, Id dialogue) {
        DialogueTree tree = trees.apply(dialogue);
        if (tree == null || tree.nodes == null || !tree.nodes.containsKey(tree.start)) return null;
        return enter(player, tree, tree.start);
    }

    /** @return the next view, or null when the dialogue ended (the caller fires DIALOGUE_FINISHED) */
    public View choose(UUID player, int optionIndex) {
        Active a = sessions.get(player);
        if (a == null) return null;
        DialogueTree.Node node = a.tree.nodes.get(a.node);
        List<DialogueTree.Option> available = available(node, player);
        String next;
        if (available.isEmpty()) {
            next = node.next;
        } else {
            if (optionIndex < 0 || optionIndex >= available.size()) return view(a.tree, a.node, player);
            DialogueTree.Option opt = available.get(optionIndex);
            runner.accept(opt.actions, player);
            next = opt.next;
        }
        if (next == null || !a.tree.nodes.containsKey(next)) {
            sessions.remove(player);
            return null;
        }
        return enter(player, a.tree, next);
    }

    public Id activeDialogue(UUID player) {
        Active a = sessions.get(player);
        return a == null ? null : a.tree.id;
    }

    public void end(UUID player) {
        sessions.remove(player);
    }

    private View enter(UUID player, DialogueTree tree, String nodeId) {
        sessions.put(player, new Active(tree, nodeId));
        DialogueTree.Node node = tree.nodes.get(nodeId);
        runner.accept(node.actions, player);
        return view(tree, nodeId, player);
    }

    private View view(DialogueTree tree, String nodeId, UUID player) {
        DialogueTree.Node node = tree.nodes.get(nodeId);
        List<String> options = new ArrayList<>();
        for (DialogueTree.Option o : available(node, player)) options.add(o.text);
        boolean last = options.isEmpty() && (node.next == null || !tree.nodes.containsKey(node.next));
        return new View(tree.id, node.speaker, node.portrait, node.text, node.voice, options, last);
    }

    private List<DialogueTree.Option> available(DialogueTree.Node node, UUID player) {
        List<DialogueTree.Option> out = new ArrayList<>();
        if (node.options == null) return out;
        Facts f = facts.apply(player);
        for (DialogueTree.Option o : node.options) {
            if (o != null && Condition.allMatch(o.conditions, f)) out.add(o);
        }
        return out;
    }
}

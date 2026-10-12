package dev.playtime.engine.admin;

import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.content.ContentLoader;
import dev.playtime.engine.content.ContentPack;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.monster.MonsterDefinition;
import dev.playtime.engine.profile.PlayerProfile;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.progress.SaveSlot;
import dev.playtime.engine.puzzle.PuzzleDefinition;
import dev.playtime.engine.puzzle.PuzzleState;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Issue;
import dev.playtime.engine.util.IssueList;
import dev.playtime.engine.validate.MapValidator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Logic behind {@code /playtime ...}. Every method checks the actor's permission first,
 * so the command layer cannot accidentally expose a privileged action. {@code actor == null}
 * means the server console.
 */
public final class AdminService {
    private final PlaytimeEngine e;

    public AdminService(PlaytimeEngine engine) {
        this.e = engine;
    }

    public boolean isAdmin(UUID actor) {
        return actor == null || e.bridge().isOperator(actor);
    }

    private AdminResult denied() {
        return AdminResult.fail("You need operator permission for this command.");
    }

    // ---------------------------------------------------------------- inspection

    public AdminResult inspect(UUID actor, UUID target) {
        if (!isAdmin(actor)) return denied();
        PlayerProgress p = e.progression().progress(target).orElse(null);
        String name = e.bridge().playerName(target);
        if (p == null) return AdminResult.ok(name + " has not started a game.");
        List<String> lines = new ArrayList<>();
        lines.add("Progress of " + name + (e.progression().isShared() ? " (shared save)" : ""));
        lines.add("Chapter: " + p.chapter + "  Checkpoint: " + p.checkpoint + "  Deaths: " + p.deaths);
        lines.add("GrabPack: " + p.grabPack + "  Left: " + p.selectedLeftHand + "  Right: " + p.selectedRightHand);
        lines.add("Hands: " + p.unlockedHands);
        lines.add("Keys: " + p.keys);
        lines.add("Puzzles: " + p.completedPuzzles);
        lines.add("Chases: " + p.completedChases);
        lines.add("Collectibles: " + p.collectibles.size() + "  Events: " + p.triggeredEvents.size()
                + "  Monsters met: " + p.encounteredMonsters);
        lines.add("Objectives: " + p.objectives);
        lines.add("Completed: " + p.gameCompleted + (p.endingReached != null ? " (" + p.endingReached + ")" : ""));
        PlayerProfile profile = e.sessions().profileOf(target);
        lines.add("Profile: " + (profile == null ? "none" : profile.id));
        return AdminResult.ok(lines);
    }

    // ---------------------------------------------------------------- hands

    public AdminResult giveHand(UUID actor, UUID target, Id hand) {
        if (!isAdmin(actor)) return denied();
        if (e.hands().registry().get(hand).isEmpty()) return AdminResult.fail("Unknown hand " + hand);
        boolean added = e.grantHand(target, hand, true);
        return AdminResult.ok(added ? "Gave " + hand + " to " + e.bridge().playerName(target) : "Already unlocked.");
    }

    public AdminResult revokeHand(UUID actor, UUID target, Id hand) {
        if (!isAdmin(actor)) return denied();
        boolean removed = e.hands().revoke(target, hand);
        e.bridge().syncPlayer(target);
        return AdminResult.ok(removed ? "Revoked " + hand : "Player did not have " + hand);
    }

    // ---------------------------------------------------------------- checkpoints & saves

    public AdminResult setCheckpoint(UUID actor, UUID target, Id checkpoint, boolean teleport) {
        if (!isAdmin(actor)) return denied();
        e.setCheckpoint(target, checkpoint, false);
        if (teleport) e.respawnAtCheckpoint(target, checkpoint);
        return AdminResult.ok("Checkpoint set to " + checkpoint);
    }

    /** Deletes a player's saves (or the shared save) so a map can be retested from scratch. */
    public AdminResult resetSave(UUID actor, UUID target, boolean alsoWorld) {
        if (!isAdmin(actor)) return denied();
        if (e.progression().isShared()) {
            SaveSlot shared = e.progression().sharedSlot();
            shared.progress = null;
            e.progression().markDirty(target);
        } else {
            e.progression().store().delete(target);
        }
        if (alsoWorld) e.sessions().resetWorldRuntime();
        e.bridge().syncPlayer(target);
        return AdminResult.ok("Save reset" + (alsoWorld ? " and world devices restored." : "."));
    }

    // ---------------------------------------------------------------- puzzles

    public AdminResult resetPuzzle(UUID actor, Id puzzle) {
        if (!isAdmin(actor)) return denied();
        if (puzzle == null) {
            int n = 0;
            for (PuzzleDefinition def : e.puzzles().definitions()) if (e.puzzles().reset(def.id, true)) n++;
            return AdminResult.ok("Reset " + n + " puzzles.");
        }
        return e.puzzles().reset(puzzle, true) ? AdminResult.ok("Reset " + puzzle) : AdminResult.fail("Unknown puzzle " + puzzle);
    }

    public AdminResult puzzleStatus(UUID actor, Id puzzle) {
        if (!isAdmin(actor)) return denied();
        List<String> lines = new ArrayList<>();
        for (PuzzleDefinition def : e.puzzles().definitions()) {
            if (puzzle != null && !def.id.equals(puzzle)) continue;
            PuzzleState s = e.puzzles().state(def.id);
            lines.add(def.id + " [" + def.template.path() + "] " + s.status + " attempts=" + s.attempts
                    + (s.stage > 0 ? " stage=" + s.stage : "") + " data=" + s.data);
        }
        return lines.isEmpty() ? AdminResult.fail("No puzzles found.") : AdminResult.ok(lines);
    }

    // ---------------------------------------------------------------- chases & monsters

    public AdminResult startChase(UUID actor, Id chase, UUID player) {
        if (!isAdmin(actor)) return denied();
        String err = e.chases().start(chase, player, true);
        return err == null ? AdminResult.ok("Started chase " + chase) : AdminResult.fail(err);
    }

    public AdminResult stopChase(UUID actor, Id chase) {
        if (!isAdmin(actor)) return denied();
        return e.chases().stop(chase) ? AdminResult.ok("Stopped " + chase) : AdminResult.fail("Chase is not running.");
    }

    public AdminResult spawnMonster(UUID actor, Id monster, BlockRef pos) {
        if (!isAdmin(actor)) return denied();
        UUID id = e.director().spawn(monster, pos, null, null, true);
        return id != null ? AdminResult.ok("Spawned " + monster) : AdminResult.fail("Could not spawn " + monster
                + (e.config().isMonsterEnabled(monster) ? "" : " (disabled in this world)"));
    }

    public AdminResult listMonsters(UUID actor) {
        if (!isAdmin(actor)) return denied();
        List<String> lines = new ArrayList<>();
        for (MonsterDefinition m : e.monsters().all()) {
            long alive = e.director().instances().stream().filter(i -> i.definition.id.equals(m.id)).count();
            lines.add(m.id + " (" + m.displayName + ", " + m.behaviour.name().toLowerCase() + ")"
                    + (e.config().isMonsterEnabled(m.id) ? "" : " [disabled]") + (alive > 0 ? " alive: " + alive : ""));
        }
        return AdminResult.ok(lines);
    }

    public AdminResult despawnAll(UUID actor) {
        if (!isAdmin(actor)) return denied();
        int n = e.director().instances().size();
        for (var i : List.copyOf(e.director().instances())) e.director().despawn(i.entity);
        return AdminResult.ok("Despawned " + n + " monsters.");
    }

    // ---------------------------------------------------------------- story

    public AdminResult triggerEvent(UUID actor, Id event, UUID player) {
        if (!isAdmin(actor)) return denied();
        if (e.story().get(event).isEmpty()) return AdminResult.fail("Unknown event " + event);
        e.story().fire(event, player, true);
        return AdminResult.ok("Fired " + event);
    }

    // ---------------------------------------------------------------- connections

    public AdminResult connections(UUID actor, Id device) {
        if (!isAdmin(actor)) return denied();
        List<String> lines = new ArrayList<>();
        for (Connection c : e.graph().connections()) {
            if (device != null && !device.equals(c.from) && !device.equals(c.to)) continue;
            lines.add(c.id + " \"" + c.displayName() + "\": " + c.from + " -[" + c.type.name().toLowerCase()
                    + (c.delayTicks > 0 ? " +" + c.delayTicks + "t" : "") + (c.enabled ? "" : " disabled") + "]-> " + c.to
                    + (c.targetRef != null ? " (" + c.targetRef + ")" : ""));
        }
        if (device != null) {
            Device d = e.graph().device(device).orElse(null);
            if (d == null) return AdminResult.fail("Unknown device " + device);
            var s = e.graph().state(device);
            lines.add(0, d.displayName() + " " + d.type + " at " + d.pos + " active=" + s.active + " powered=" + s.powered
                    + " inputs=" + s.activeInputs.size() + "/" + d.inputsRequired + " sockets=" + s.sockets);
            for (Id p : e.puzzles().puzzlesWatching(device)) lines.add("watched by puzzle " + p);
        }
        return lines.isEmpty() ? AdminResult.ok("No connections.") : AdminResult.ok(lines);
    }

    public AdminResult removeConnection(UUID actor, Id connection) {
        if (!isAdmin(actor)) return denied();
        return e.interactions().disconnect(connection) ? AdminResult.ok("Removed " + connection) : AdminResult.fail("No such link.");
    }

    public AdminResult renameConnection(UUID actor, Id connection, String name) {
        if (!isAdmin(actor)) return denied();
        return e.graph().rename(connection, name) ? AdminResult.ok("Renamed.") : AdminResult.fail("No such link.");
    }

    public AdminResult testDevice(UUID actor, Id device) {
        if (!isAdmin(actor)) return denied();
        if (e.graph().device(device).isEmpty()) return AdminResult.fail("Unknown device " + device);
        e.interactions().test(device);
        return AdminResult.ok("Pulsed " + device);
    }

    // ---------------------------------------------------------------- map tools

    public AdminResult validate(UUID actor) {
        if (!isAdmin(actor)) return denied();
        IssueList issues = MapValidator.validate(e);
        List<String> lines = new ArrayList<>();
        lines.add("Validation: " + issues.count(Issue.Severity.ERROR) + " errors, "
                + issues.count(Issue.Severity.WARNING) + " warnings, " + issues.count(Issue.Severity.INFO) + " notes");
        for (Issue i : issues.all()) lines.add(i.toString());
        return new AdminResult(!issues.hasErrors(), lines);
    }

    public AdminResult reload(UUID actor) {
        if (!isAdmin(actor)) return denied();
        if (e.root() == null) return AdminResult.fail("This world has no content folder.");
        IssueList issues = new IssueList();
        ContentPack fresh = ContentLoader.load(e.root().resolve("content"), true, issues);
        e.replaceContent(fresh);
        issues.addAll(e.rebuild());
        return AdminResult.ok("Reloaded content: " + issues.count(Issue.Severity.ERROR) + " errors, "
                + issues.count(Issue.Severity.WARNING) + " warnings. Run /playtime validate for details.");
    }

    public AdminResult backup(UUID actor) {
        if (!isAdmin(actor)) return denied();
        try {
            Path dir = e.backup();
            return AdminResult.ok("Backed up to " + dir);
        } catch (IOException ex) {
            return AdminResult.fail("Backup failed: " + ex.getMessage());
        }
    }

    public AdminResult setMenuBypass(UUID actor, UUID target, boolean on) {
        if (!isAdmin(actor)) return denied();
        e.menus().setBypass(target, on);
        return AdminResult.ok("Main menu bypass " + (on ? "enabled" : "disabled") + " for " + e.bridge().playerName(target));
    }

    /** Players may reopen the menu themselves when the world allows it; admins can open it for anyone. */
    public AdminResult openMenu(UUID actor, UUID target) {
        boolean self = actor != null && actor.equals(target);
        if (!(isAdmin(actor) || self && e.config().rules.allowMenuCommand)) return denied();
        e.returnToMenu(target);
        return AdminResult.ok("Opening the menu.");
    }

    public AdminResult assignProfile(UUID actor, UUID target, Id profile) {
        if (!isAdmin(actor)) return denied();
        if (profile != null && e.profiles().get(profile).isEmpty()) return AdminResult.fail("Unknown profile " + profile);
        e.sessions().assignProfile(target, profile);
        return AdminResult.ok(profile == null ? "Cleared profile assignment." : "Assigned " + profile);
    }

    public AdminResult listProfiles(UUID actor) {
        if (!isAdmin(actor)) return denied();
        List<String> lines = new ArrayList<>();
        for (PlayerProfile p : e.profiles().all()) {
            lines.add(p.id + " \"" + p.displayName + "\" hands=" + p.startingHands + " items=" + p.inventory.size()
                    + (p.id.equals(e.config().defaultProfile) ? " (default)" : ""));
        }
        return AdminResult.ok(lines);
    }

    /** Re-applies a profile to the player's current save (testing kits). */
    public AdminResult applyProfile(UUID actor, UUID target, Id profile) {
        if (!isAdmin(actor)) return denied();
        PlayerProfile p = e.profiles().get(profile).orElse(null);
        if (p == null) return AdminResult.fail("Unknown profile " + profile);
        var kit = dev.playtime.engine.profile.ProfileApplier.apply(p, e.progression().progressOrCreate(target));
        e.progression().markDirty(target);
        e.bridge().applyStartingKit(target, kit);
        e.bridge().syncPlayer(target);
        return AdminResult.ok("Applied " + profile);
    }

    public AdminResult kinds(UUID actor) {
        if (!isAdmin(actor)) return denied();
        List<String> lines = new ArrayList<>();
        for (ContentKind k : ContentKind.values()) lines.add(k.folder + ": " + e.content().size(k));
        return AdminResult.ok(lines);
    }
}

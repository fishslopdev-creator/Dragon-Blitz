package dev.playtime.engine.validate;

import dev.playtime.engine.access.DoorDefinition;
import dev.playtime.engine.chase.ChaseBranch;
import dev.playtime.engine.chase.ChaseDefinition;
import dev.playtime.engine.chase.ChaseStage;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.content.ContentPack;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.ConnectionType;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.hand.HandAbilities;
import dev.playtime.engine.hand.HandDefinition;
import dev.playtime.engine.hand.HandPickupConfig;
import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.monster.MonsterDefinition;
import dev.playtime.engine.monster.RouteDefinition;
import dev.playtime.engine.profile.PlayerProfile;
import dev.playtime.engine.puzzle.PuzzleDefinition;
import dev.playtime.engine.puzzle.PuzzleLogic;
import dev.playtime.engine.story.ChapterDefinition;
import dev.playtime.engine.story.CollectibleDefinition;
import dev.playtime.engine.story.DialogueTree;
import dev.playtime.engine.story.StoryEvent;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IssueList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cross-checks a whole map: dangling ids, unreachable waypoints, puzzles watching
 * missing devices, abilities nobody handles, unknown sounds keys and so on.
 * Used by {@code /playtime validate} and the World Settings screen.
 */
public final class MapValidator {
    private static final Set<Id> KNOWN_ABILITIES = knownAbilities();

    private final PlaytimeEngine engine;
    private final ContentPack c;
    private final IssueList issues = new IssueList();

    private MapValidator(PlaytimeEngine engine) {
        this.engine = engine;
        this.c = engine.content();
    }

    public static IssueList validate(PlaytimeEngine engine) {
        MapValidator v = new MapValidator(engine);
        v.issues.addAll(engine.loadIssues());
        v.run();
        return v.issues;
    }

    private void run() {
        hands();
        doors();
        puzzles();
        monsters();
        chases();
        events();
        dialogues();
        profiles();
        devices();
        chapters();
        pickups();
        for (String w : engine.director().warnings()) issues.warning("monster AI", w);
    }

    private void hands() {
        for (HandDefinition h : c.<HandDefinition>all(ContentKind.HANDS)) {
            String src = "hand " + h.id;
            if (h.abilities.isEmpty()) issues.warning(src, "has no abilities");
            for (Id a : h.abilities) {
                if (!KNOWN_ABILITIES.contains(a)) issues.warning(src, "ability " + a + " has no built-in handler");
            }
            if (h.icon == null) issues.warning(src, "has no icon");
            if (h.replaces != null && !c.contains(ContentKind.HANDS, h.replaces)) issues.error(src, "replaces unknown hand " + h.replaces);
            if (h.tutorial != null && !c.contains(ContentKind.EVENTS, h.tutorial)) issues.error(src, "tutorial event " + h.tutorial + " does not exist");
            for (Id pack : h.grabPacks) if (!c.contains(ContentKind.GRABPACKS, pack)) issues.error(src, "unknown GrabPack " + pack);
            for (Id req : h.unlock.hands) if (!c.contains(ContentKind.HANDS, req)) issues.error(src, "unlock needs unknown hand " + req);
        }
    }

    private void doors() {
        for (DoorDefinition d : c.<DoorDefinition>all(ContentKind.DOORS)) {
            String src = "door " + d.id;
            if (d.anchor == null) issues.warning(src, "has no anchor block; place a Door Controller and link it");
            for (Id cred : d.access.credentials) if (!c.contains(ContentKind.CREDENTIALS, cred)) issues.error(src, "unknown credential " + cred);
            for (Id h : d.access.requiredHands) if (!c.contains(ContentKind.HANDS, h)) issues.error(src, "unknown hand " + h);
            for (Id h : d.access.scanHands) if (!c.contains(ContentKind.HANDS, h)) issues.error(src, "unknown scan hand " + h);
            conditions(src, d.access.conditions);
        }
    }

    private void puzzles() {
        for (PuzzleDefinition p : c.<PuzzleDefinition>all(ContentKind.PUZZLES)) {
            String src = "puzzle " + p.id;
            PuzzleLogic logic = engine.puzzles().templates().get(p.template).orElse(null);
            if (logic == null) {
                issues.error(src, "unknown template " + p.template);
                continue;
            }
            for (Id dev : engine.puzzles().devicesOf(p)) {
                if (engine.graph().device(dev).isEmpty() && !c.contains(ContentKind.PUZZLES, dev)) {
                    issues.warning(src, "watches device " + dev + " which has not been placed yet");
                }
            }
            for (Id door : p.linkedDoors) if (!c.contains(ContentKind.DOORS, door)) issues.error(src, "links unknown door " + door);
            for (Id h : p.requiredHands) if (!c.contains(ContentKind.HANDS, h)) issues.error(src, "requires unknown hand " + h);
            conditions(src, p.activation);
            conditions(src, p.success);
            actions(src, p.onStart);
            actions(src, p.onComplete);
            actions(src, p.onFail);
            actions(src, p.rewards);
        }
    }

    private void monsters() {
        for (MonsterDefinition m : c.<MonsterDefinition>all(ContentKind.MONSTERS)) {
            String src = "monster " + m.id;
            if (m.model == null) issues.warning(src, "has no model; it will render as a placeholder");
            if (m.patrolRoute != null && !c.contains(ContentKind.ROUTES, m.patrolRoute)) issues.error(src, "unknown patrol route " + m.patrolRoute);
            if (m.introEvent != null && !c.contains(ContentKind.EVENTS, m.introEvent)) issues.error(src, "unknown intro event " + m.introEvent);
            if (m.dialogue != null && !c.contains(ContentKind.DIALOGUES, m.dialogue)) issues.error(src, "unknown dialogue " + m.dialogue);
            if (m.chaseSpeed < m.walkSpeed) issues.warning(src, "chase speed is slower than walk speed");
            conditions(src, m.spawnConditions);
            actions(src, m.onCatch);
        }
        for (RouteDefinition r : c.<RouteDefinition>all(ContentKind.ROUTES)) {
            String src = "route " + r.id;
            if (r.points.size() < 2) issues.warning(src, "has fewer than two waypoints");
            for (int i = 1; i < r.points.size(); i++) {
                var a = r.points.get(i - 1).pos;
                var b = r.points.get(i).pos;
                if (a == null || b == null) {
                    issues.error(src, "waypoint " + i + " has no position");
                } else if (!a.dimension().equals(b.dimension())) {
                    issues.error(src, "waypoints " + i + " and " + (i + 1) + " are in different dimensions");
                } else if (Math.sqrt(a.distanceSq(b)) > 64) {
                    issues.warning(src, "waypoints " + i + " and " + (i + 1) + " are over 64 blocks apart; pathfinding may give up");
                }
            }
        }
    }

    private void chases() {
        for (ChaseDefinition ch : c.<ChaseDefinition>all(ContentKind.CHASES)) {
            String src = "chase " + ch.id;
            if (ch.monster == null || !c.contains(ContentKind.MONSTERS, ch.monster)) issues.error(src, "unknown monster " + ch.monster);
            if (ch.spawn == null) issues.error(src, "has no spawn position");
            if (ch.route != null && !c.contains(ContentKind.ROUTES, ch.route)) issues.error(src, "unknown route " + ch.route);
            if (ch.checkpoint == null) issues.warning(src, "has no checkpoint; failed players respawn at their save checkpoint");
            if (ch.stages.isEmpty()) issues.warning(src, "has no stages; it completes immediately");
            for (Id p : ch.resetPuzzles) if (!c.contains(ContentKind.PUZZLES, p)) issues.error(src, "resets unknown puzzle " + p);
            actions(src, ch.onStart);
            actions(src, ch.onComplete);
            actions(src, ch.onFail);
            for (ChaseStage s : ch.stages) {
                String ss = src + " stage " + s.id;
                if (s.success.isEmpty() && s.branches.isEmpty()) issues.warning(ss, "has no success conditions; it ends instantly");
                if (s.next != null && !"complete".equals(s.next) && !"fail".equals(s.next) && ch.stageIndex(s.next) < 0) {
                    issues.error(ss, "next stage '" + s.next + "' does not exist");
                }
                if (s.monsterRoute != null && !c.contains(ContentKind.ROUTES, s.monsterRoute)) issues.error(ss, "unknown route " + s.monsterRoute);
                for (ChaseBranch b : s.branches) {
                    if (b.next == null || !"complete".equals(b.next) && !"fail".equals(b.next) && ch.stageIndex(b.next) < 0) {
                        issues.error(ss, "branch goes to unknown stage '" + b.next + "'");
                    }
                    conditions(ss, b.conditions);
                    actions(ss, b.actions);
                }
                conditions(ss, s.success);
                actions(ss, s.onEnter);
                actions(ss, s.onSuccess);
            }
        }
    }

    private void events() {
        for (StoryEvent ev : c.<StoryEvent>all(ContentKind.EVENTS)) {
            String src = "event " + ev.id;
            conditions(src, ev.conditions);
            actions(src, ev.actions);
            for (Action a : ev.actions) {
                if (a != null && a.type == dev.playtime.engine.logic.ActionType.TRIGGER_EVENT && ev.id.equals(a.id) && a.delay == 0) {
                    issues.error(src, "triggers itself with no delay");
                }
            }
        }
    }

    private void dialogues() {
        for (DialogueTree t : c.<DialogueTree>all(ContentKind.DIALOGUES)) {
            String src = "dialogue " + t.id;
            if (t.nodes == null || !t.nodes.containsKey(t.start)) {
                issues.error(src, "start node '" + t.start + "' is missing");
                continue;
            }
            for (var en : t.nodes.entrySet()) {
                DialogueTree.Node n = en.getValue();
                if (n.next != null && !t.nodes.containsKey(n.next)) issues.error(src, "node " + en.getKey() + " goes to missing node " + n.next);
                if (n.options != null) {
                    for (DialogueTree.Option o : n.options) {
                        if (o.next != null && !t.nodes.containsKey(o.next)) issues.error(src, "option '" + o.text + "' goes to missing node " + o.next);
                        actions(src, o.actions);
                    }
                }
                actions(src, n.actions);
            }
        }
    }

    private void profiles() {
        if (!c.contains(ContentKind.PROFILES, engine.config().defaultProfile)) {
            issues.warning("world config", "default profile " + engine.config().defaultProfile + " does not exist");
        }
        for (PlayerProfile p : c.<PlayerProfile>all(ContentKind.PROFILES)) {
            String src = "profile " + p.id;
            for (Id h : p.startingHands) if (!c.contains(ContentKind.HANDS, h)) issues.error(src, "unknown starting hand " + h);
            if (p.giveGrabPack && p.grabPack != null && !c.contains(ContentKind.GRABPACKS, p.grabPack)) {
                issues.error(src, "unknown GrabPack " + p.grabPack);
            }
            if (p.startingCheckpoint != null && !c.contains(ContentKind.CHECKPOINTS, p.startingCheckpoint)
                    && engine.graph().device(p.startingCheckpoint).isEmpty()) {
                issues.error(src, "unknown starting checkpoint " + p.startingCheckpoint);
            }
        }
    }

    private void devices() {
        for (Device d : engine.graph().devices()) {
            String src = "device " + d.id;
            ref(src, d, "door", ContentKind.DOORS);
            ref(src, d, "chase", ContentKind.CHASES);
            ref(src, d, "event", ContentKind.EVENTS);
            ref(src, d, "objective", ContentKind.OBJECTIVES);
            ref(src, d, "chapter", ContentKind.CHAPTERS);
            ref(src, d, "monster", ContentKind.MONSTERS);
            ref(src, d, "route", ContentKind.ROUTES);
            ref(src, d, "pickup", ContentKind.PICKUPS);
            ref(src, d, "hand", ContentKind.HANDS);
            ref(src, d, "collectible", ContentKind.COLLECTIBLES);
            ref(src, d, "dialogue", ContentKind.DIALOGUES);
            if (d.type == DeviceType.CHASE_TRIGGER && d.idSetting("chase") == null) issues.error(src, "chase trigger has no chase");
            if (d.type == DeviceType.MONSTER_SPAWN && d.idSetting("monster") == null) issues.error(src, "spawn marker has no monster");
            if (d.type == DeviceType.DOOR_CONTROLLER && d.idSetting("door") == null) issues.warning(src, "door controller has no door");
            boolean linked = !engine.graph().outgoing(d.id).isEmpty() || !engine.graph().incoming(d.id).isEmpty()
                    || !engine.puzzles().puzzlesWatching(d.id).isEmpty();
            boolean selfContained = switch (d.type) {
                case CHECKPOINT, CHASE_TRIGGER, STORY_TRIGGER, OBJECTIVE_TRIGGER, CHAPTER_TRANSITION, HAND_PICKUP,
                     SETUP_CONSOLE, MONSTER_SPAWN, ROUTE_MARKER, DOOR_CONTROLLER -> true;
                default -> false;
            };
            if (!linked && !selfContained && d.settings.isEmpty()) issues.info(src, "is not linked to anything");
            if (d.inputsRequired > 1 && engine.graph().incoming(d.id).stream()
                    .filter(x -> x.type == ConnectionType.ACTIVATE).count() < d.inputsRequired) {
                issues.error(src, "needs " + d.inputsRequired + " inputs but has fewer ACTIVATE links");
            }
        }
        for (Connection conn : engine.graph().connections()) {
            if (conn.targetRef == null) continue;
            String src = "link " + conn.displayName();
            ContentKind kind = switch (conn.type) {
                case OPEN_DOOR, CLOSE_DOOR, LOCK_DOOR, UNLOCK_DOOR -> ContentKind.DOORS;
                case START_CHASE, STOP_CHASE -> ContentKind.CHASES;
                case TRIGGER_EVENT -> ContentKind.EVENTS;
                case RESET_PUZZLE -> ContentKind.PUZZLES;
                default -> null;
            };
            if (kind != null && !c.contains(kind, conn.targetRef)) issues.error(src, "refers to unknown " + kind.folder + " entry " + conn.targetRef);
        }
    }

    private void chapters() {
        for (ChapterDefinition ch : c.<ChapterDefinition>all(ContentKind.CHAPTERS)) {
            String src = "chapter " + ch.id;
            if (ch.next != null && !c.contains(ContentKind.CHAPTERS, ch.next)) issues.error(src, "next chapter " + ch.next + " does not exist");
            actions(src, ch.onStart);
            actions(src, ch.onComplete);
        }
        if (!c.contains(ContentKind.CHAPTERS, engine.config().startingChapter)) {
            issues.warning("world config", "starting chapter " + engine.config().startingChapter + " has no chapter definition");
        }
        for (CollectibleDefinition col : c.<CollectibleDefinition>all(ContentKind.COLLECTIBLES)) actions("collectible " + col.id, col.onCollect);
    }

    private void pickups() {
        for (HandPickupConfig p : c.<HandPickupConfig>all(ContentKind.PICKUPS)) {
            if (p.hand == null || !c.contains(ContentKind.HANDS, p.hand)) issues.error("pickup " + p.id, "unknown hand " + p.hand);
        }
    }

    private void ref(String src, Device d, String key, ContentKind kind) {
        Id id = d.idSetting(key);
        if (id != null && !c.contains(kind, id)) issues.error(src, key + " " + id + " does not exist");
    }

    private void conditions(String src, List<Condition> list) {
        if (list == null) return;
        List<Condition> flat = new ArrayList<>();
        for (Condition cond : list) if (cond != null) cond.collectReferences(flat);
        for (Condition cond : flat) {
            if (cond.type == null) {
                issues.error(src, "condition without a type");
                continue;
            }
            ContentKind kind = switch (cond.type) {
                case HAS_HAND, SELECTED_HAND -> ContentKind.HANDS;
                case HAS_KEY -> ContentKind.CREDENTIALS;
                case PUZZLE_COMPLETED -> ContentKind.PUZZLES;
                case EVENT_TRIGGERED -> ContentKind.EVENTS;
                case OBJECTIVE_COMPLETED, OBJECTIVE_ACTIVE -> ContentKind.OBJECTIVES;
                case CHAPTER -> ContentKind.CHAPTERS;
                case DOOR_OPEN -> ContentKind.DOORS;
                case CHASE_COMPLETED -> ContentKind.CHASES;
                case COLLECTIBLE -> ContentKind.COLLECTIBLES;
                default -> null;
            };
            if (kind != null && cond.id != null && !c.contains(kind, cond.id)) {
                issues.error(src, "condition " + cond.type.name().toLowerCase() + " refers to unknown " + cond.id);
            }
            if ((cond.type == dev.playtime.engine.logic.ConditionType.DEVICE_POWERED
                    || cond.type == dev.playtime.engine.logic.ConditionType.DEVICE_ACTIVE)
                    && cond.id != null && engine.graph().device(cond.id).isEmpty()) {
                issues.warning(src, "condition refers to device " + cond.id + " which has not been placed yet");
            }
        }
    }

    private void actions(String src, List<Action> list) {
        if (list == null) return;
        for (Action a : list) {
            if (a == null || a.type == null) {
                issues.error(src, "action without a type");
                continue;
            }
            ContentKind kind = switch (a.type) {
                case DIALOGUE -> ContentKind.DIALOGUES;
                case PLAY_RECORDING, JOURNAL_ADD -> ContentKind.COLLECTIBLES;
                case OBJECTIVE_SET, OBJECTIVE_COMPLETE, OBJECTIVE_FAIL -> ContentKind.OBJECTIVES;
                case GRANT_HAND -> ContentKind.HANDS;
                case GIVE_KEY, TAKE_KEY -> ContentKind.CREDENTIALS;
                case SET_CHAPTER -> ContentKind.CHAPTERS;
                case DOOR_OPEN, DOOR_CLOSE, DOOR_LOCK, DOOR_UNLOCK -> ContentKind.DOORS;
                case RESET_PUZZLE -> ContentKind.PUZZLES;
                case START_CHASE, STOP_CHASE -> ContentKind.CHASES;
                case TRIGGER_EVENT -> ContentKind.EVENTS;
                case DESPAWN_MONSTER -> ContentKind.MONSTERS;
                default -> null;
            };
            if (kind != null && a.id == null) issues.error(src, a.type.name().toLowerCase() + " action needs an id");
            if (kind != null && a.id != null && !c.contains(kind, a.id)) {
                issues.error(src, a.type.name().toLowerCase() + " refers to unknown " + a.id);
            }
            if (a.type == dev.playtime.engine.logic.ActionType.SPAWN_MONSTER && a.id != null
                    && !c.contains(ContentKind.MONSTERS, a.id) && engine.graph().device(a.id).isEmpty()) {
                issues.error(src, "spawn_monster refers to unknown monster or spawn marker " + a.id);
            }
            if (a.type == dev.playtime.engine.logic.ActionType.SPAWN_MONSTER && a.id != null
                    && c.contains(ContentKind.MONSTERS, a.id) && a.pos == null) {
                issues.error(src, "spawn_monster needs a 'pos' when given a monster id");
            }
        }
    }

    private static Set<Id> knownAbilities() {
        Set<Id> out = new java.util.HashSet<>();
        for (Field f : HandAbilities.class.getFields()) {
            try {
                if (f.get(null) instanceof Id id) out.add(id);
            } catch (IllegalAccessException ignored) {
                // public constants only
            }
        }
        return out;
    }
}

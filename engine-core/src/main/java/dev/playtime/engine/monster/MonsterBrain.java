package dev.playtime.engine.monster;

import dev.playtime.engine.config.TargetPolicy;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Decision making for one monster. Runs on the server, every {@code thinkInterval} ticks,
 * against a {@link MonsterSenses} snapshot, and returns a {@link MonsterIntent}.
 * Contains no Minecraft types, so it can be unit tested and reused by any entity model.
 */
public final class MonsterBrain {
    private static final int MAX_WARNINGS = 16;

    private final MonsterDefinition def;
    private final double difficulty;
    private final TargetPolicy policy;
    private final Vec3 home;
    private final long seed;

    private MonsterState state;
    private RecoveryStrategy recovery;
    private List<Vec3> route = List.of();
    private boolean routeLoop = true;
    private int routeIndex;
    private long waitUntil;

    private UUID target;
    private Vec3 lastKnown;
    private long lastSeenTick = Long.MIN_VALUE / 2;
    private Vec3 investigatePoint;
    private Vec3 searchPoint;
    private long stateSince;

    private long sprintUntil;
    private long sprintReadyAt;
    private long attackReadyAt;
    private long stunnedUntil;
    private long lockedChaseUntil;

    private boolean chaseLocked;
    private UUID forcedTarget;
    private List<Vec3> chaseRoute = List.of();
    private int chaseRouteIndex;
    private Double speedOverride;

    private Vec3 trackedGoal;
    private double bestGoalDistance = Double.MAX_VALUE;
    private long progressSince;

    private long nextThink;
    private MonsterIntent lastIntent;
    private final Deque<Noise> heard = new ArrayDeque<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean alerted;

    public MonsterBrain(MonsterDefinition def, double difficulty, TargetPolicy policy, Vec3 home, long seed) {
        this.def = def.normalize();
        this.difficulty = Math.max(0.1, difficulty);
        this.policy = policy == null ? TargetPolicy.NEAREST : policy;
        this.home = home;
        this.seed = seed;
        this.state = switch (def.behaviour) {
            case AMBUSHER -> MonsterState.AMBUSH_WAIT;
            case NPC, SCRIPTED, BOSS -> MonsterState.IDLE;
            default -> MonsterState.PATROL;
        };
        this.lastIntent = MonsterIntent.idle(state, def.animation("idle"));
        this.recovery = def.recovery;
    }

    /** Chases may override how a stuck monster recovers. */
    public void setRecovery(RecoveryStrategy strategy) {
        this.recovery = strategy == null ? def.recovery : strategy;
    }

    // ------------------------------------------------------------------ commands

    public MonsterDefinition definition() { return def; }

    public MonsterState state() { return state; }

    public UUID target() { return target; }

    public List<String> warnings() { return warnings; }

    public void setRoute(List<Vec3> points, boolean loop) {
        this.route = points == null ? List.of() : List.copyOf(points);
        this.routeLoop = loop;
        this.routeIndex = nearestIndex(this.route, home);
    }

    /** Locks the monster into a chase (used by the chase controller). */
    public void beginChase(UUID preferredTarget, List<Vec3> chaseWaypoints, Double speed, long tick) {
        chaseLocked = true;
        forcedTarget = preferredTarget;
        chaseRoute = chaseWaypoints == null ? List.of() : List.copyOf(chaseWaypoints);
        chaseRouteIndex = 0;
        speedOverride = speed;
        target = preferredTarget;
        enter(MonsterState.CHASE, tick);
        nextThink = tick;
    }

    public void endChase(long tick) {
        chaseLocked = false;
        forcedTarget = null;
        chaseRoute = List.of();
        speedOverride = null;
        enter(def.behaviour == BehaviourProfile.AMBUSHER ? MonsterState.RETREAT : MonsterState.SEARCH, tick);
    }

    public void hear(Noise noise) {
        if (heard.size() >= 8) heard.pollFirst();
        heard.addLast(noise);
    }

    /** Applies a stun if this monster is vulnerable to {@code ability}. Returns true when stunned. */
    public boolean stun(Id ability, long tick) {
        if (def.traits.contains(MonsterTrait.IMMUNE_TO_STUN) || !def.stunnedBy.contains(ability)) return false;
        stunnedUntil = tick + def.stunTicks;
        enter(MonsterState.STUNNED, tick);
        nextThink = tick;
        return true;
    }

    public void despawn(long tick) {
        enter(MonsterState.DESPAWNED, tick);
        nextThink = tick;
    }

    // ------------------------------------------------------------------ main loop

    public MonsterIntent think(MonsterSenses senses, long tick) {
        if (tick < nextThink && lastIntent != null) return lastIntent;
        nextThink = tick + def.thinkInterval;
        lastIntent = decide(senses, tick);
        return lastIntent;
    }

    private MonsterIntent decide(MonsterSenses s, long tick) {
        if (state == MonsterState.DESPAWNED) {
            return new MonsterIntent(state, null, 0, null, def.animation("despawn"), null, null, null, true);
        }
        if (state == MonsterState.STUNNED) {
            if (tick < stunnedUntil) return MonsterIntent.idle(state, def.animation("stunned"));
            enter(chaseLocked ? MonsterState.CHASE : MonsterState.SEARCH, tick);
            searchPoint = s.position();
        }
        Vec3 pos = s.position();
        List<PlayerView> visible = visiblePlayers(s);
        Noise noise = loudestNoise(pos, s.players());
        return switch (def.behaviour) {
            case NPC -> npc(s, tick);
            case SCRIPTED -> chaseLocked ? hunt(s, visible, noise, tick) : followRoute(s, tick, def.walkSpeed);
            case BOSS -> chaseLocked || def.param("aggressive", 0) > 0 ? hunt(s, visible, noise, tick) : followRoute(s, tick, def.walkSpeed);
            case STEALTH -> stealth(s, visible, tick);
            case AMBUSHER -> ambusher(s, visible, tick);
            default -> hunt(s, visible, noise, tick);
        };
    }

    // ------------------------------------------------------------------ perception

    private double vision() {
        double v = def.visionRange * difficulty;
        return def.behaviour == BehaviourProfile.SOUND_HUNTER ? v * def.param("vision_factor", 0.35) : v;
    }

    private double hearing() {
        double h = def.hearingRange * difficulty;
        return def.behaviour == BehaviourProfile.SOUND_HUNTER ? h * 1.5 : h;
    }

    List<PlayerView> visiblePlayers(MonsterSenses s) {
        Vec3 pos = s.position();
        Vec3 eye = pos.add(new Vec3(0, def.height * 0.9, 0));
        List<PlayerView> out = new ArrayList<>();
        for (PlayerView p : s.players()) {
            double dist = p.position().distanceTo(pos);
            if (dist <= def.proximityRange) {
                out.add(p);
                continue;
            }
            if (p.hidden() || dist > vision()) continue;
            Vec3 toPlayer = p.position().subtract(pos);
            Vec3 flat = new Vec3(toPlayer.x(), 0, toPlayer.z());
            if (Vec3.angleBetween(s.facing(), flat) > def.fovDegrees / 2 && !(chaseLocked && dist < vision() * 0.5)) continue;
            if (def.needsLineOfSight && !s.hasLineOfSight(eye, p.eyePosition())) continue;
            out.add(p);
        }
        return out;
    }

    private Noise loudestNoise(Vec3 pos, List<PlayerView> players) {
        Noise best = null;
        double bestScore = 0;
        while (!heard.isEmpty()) {
            Noise n = heard.pollFirst();
            double range = hearing() * Math.max(0.1, n.loudness());
            if (!def.distractedBy.isEmpty() && n.tag() != null && def.distractedBy.contains(n.tag())) range *= 2;
            double d = n.position().distanceTo(pos);
            if (d > range) continue;
            double score = range - d;
            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }
        for (PlayerView p : players) {
            if (!p.sprinting() || p.sneaking() || p.hidden()) continue;
            double d = p.position().distanceTo(pos);
            double range = hearing() * 0.6;
            if (d <= range && range - d > bestScore) {
                bestScore = range - d;
                best = new Noise(p.position(), 0.6, Id.of("footsteps"), p.id());
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ behaviours

    private MonsterIntent hunt(MonsterSenses s, List<PlayerView> visible, Noise noise, long tick) {
        Vec3 pos = s.position();
        PlayerView seen = TargetSelector.pick(visible, policy, pos, chaseLocked ? forcedTarget : target, seed);
        double memory = def.memoryTicks * (def.behaviour == BehaviourProfile.HUNTER ? 2 : 1);
        if (seen != null) {
            target = seen.id();
            lastKnown = seen.position();
            lastSeenTick = tick;
            boolean alert = state != MonsterState.CHASE && state != MonsterState.ATTACK;
            if (alert) enter(MonsterState.CHASE, tick);
            return pursue(s, seen.position(), tick, alert);
        }
        if (state == MonsterState.CHASE || state == MonsterState.ATTACK) {
            if (lastKnown != null && tick - lastSeenTick <= memory) {
                if (pos.distanceTo(lastKnown) > 1.5) return pursue(s, lastKnown, tick, false);
            }
            if (chaseLocked) return followChaseRoute(s, tick);
            enter(MonsterState.SEARCH, tick);
            searchPoint = lastKnown != null ? lastKnown : pos;
        }
        if (noise != null && state != MonsterState.CHASE) {
            if (def.behaviour == BehaviourProfile.SWARM && noise.source() != null) {
                target = noise.source();
                lastKnown = noise.position();
                lastSeenTick = tick;
                enter(MonsterState.CHASE, tick);
                return pursue(s, noise.position(), tick, true);
            }
            investigatePoint = noise.position();
            enter(MonsterState.INVESTIGATE, tick);
        }
        if (state == MonsterState.INVESTIGATE && investigatePoint != null) {
            if (pos.distanceTo(investigatePoint) > 1.5) {
                return move(s, investigatePoint, def.walkSpeed * 1.25, "run", tick, null);
            }
            if (tick - stateSince > def.investigateTicks) {
                enter(MonsterState.SEARCH, tick);
                searchPoint = investigatePoint;
            } else {
                return look(s, "search");
            }
        }
        if (state == MonsterState.SEARCH) {
            int searchFor = def.searchTicks * (def.behaviour == BehaviourProfile.HUNTER ? 2 : 1);
            if (tick - stateSince > searchFor) {
                enter(chaseLocked ? MonsterState.CHASE : MonsterState.PATROL, tick);
            } else {
                Vec3 centre = searchPoint != null ? searchPoint : pos;
                Vec3 wander = wanderPoint(centre, tick);
                if (pos.distanceTo(wander) > 1.0) return move(s, wander, def.walkSpeed, "search", tick, null);
                return look(s, "search");
            }
        }
        if (chaseLocked) return followChaseRoute(s, tick);
        double patrolSpeed = def.behaviour == BehaviourProfile.CRAWLER ? def.chaseSpeed * 0.8 : def.walkSpeed;
        return followRoute(s, tick, patrolSpeed);
    }

    private MonsterIntent pursue(MonsterSenses s, Vec3 goal, long tick, boolean alertSound) {
        Vec3 pos = s.position();
        Id sound = alertSound && !alerted ? def.sounds.get("alert") : null;
        if (alertSound) alerted = true;
        double reach = def.attackRange * (def.traits.contains(MonsterTrait.LONG_REACH) ? 1.5 : 1.0);
        if (target != null && pos.distanceTo(goal) <= reach && def.hostile) {
            if (tick >= attackReadyAt) {
                attackReadyAt = tick + def.attackCooldownTicks;
                enter(MonsterState.ATTACK, tick);
                return new MonsterIntent(MonsterState.ATTACK, goal, 0, goal, def.animation("attack"), target, null,
                        def.sounds.get("attack"), false);
            }
            return new MonsterIntent(state, goal, 0, goal, def.animation("idle"), null, null, null, false);
        }
        if (state == MonsterState.ATTACK) state = MonsterState.CHASE;
        double speed = speedOverride != null ? speedOverride : def.chaseSpeed;
        String anim = def.behaviour == BehaviourProfile.CRAWLER ? "crawl" : "run";
        if (def.sprintDurationTicks > 0 && speedOverride == null) {
            if (tick >= sprintReadyAt && tick >= sprintUntil) {
                sprintUntil = tick + def.sprintDurationTicks;
                sprintReadyAt = sprintUntil + def.sprintCooldownTicks;
            }
            if (tick < sprintUntil) {
                speed = def.sprintSpeed;
                anim = "sprint";
            }
        }
        return move(s, goal, speed, anim, tick, sound);
    }

    private MonsterIntent stealth(MonsterSenses s, List<PlayerView> visible, long tick) {
        Vec3 pos = s.position();
        double watchedAngle = def.param("watched_angle", 25);
        PlayerView watcher = null;
        for (PlayerView p : s.players()) {
            if (p.hidden()) continue;
            Vec3 toMonster = pos.add(new Vec3(0, def.height * 0.6, 0)).subtract(p.eyePosition());
            if (toMonster.length() > vision()) continue;
            if (Vec3.angleBetween(p.lookDirection(), toMonster) <= watchedAngle
                    && s.hasLineOfSight(p.eyePosition(), pos.add(new Vec3(0, def.height * 0.6, 0)))) {
                watcher = p;
                break;
            }
        }
        // Knows where players are once one has been noticed, regardless of facing.
        PlayerView prey = TargetSelector.pick(visible.isEmpty() && target != null ? s.players() : visible,
                policy, pos, chaseLocked ? forcedTarget : target, seed);
        if (watcher != null) {
            double dist = watcher.position().distanceTo(pos);
            if (dist < def.attackRange * 0.75 && def.hostile) return pursue(s, watcher.position(), tick, false);
            enter(MonsterState.RETREAT, tick);
            Vec3 away = pos.add(pos.subtract(watcher.position()).normalize().scale(def.param("retreat_distance", 4)));
            return new MonsterIntent(MonsterState.RETREAT, away, def.walkSpeed, watcher.eyePosition(),
                    def.animation("retreat"), null, null, null, false);
        }
        if (prey != null) {
            target = prey.id();
            lastKnown = prey.position();
            lastSeenTick = tick;
            if (state != MonsterState.CHASE && state != MonsterState.ATTACK) enter(MonsterState.CHASE, tick);
            return pursueNoSprint(s, prey.position(), tick);
        }
        if (chaseLocked) return followChaseRoute(s, tick);
        return followRoute(s, tick, def.walkSpeed);
    }

    private MonsterIntent pursueNoSprint(MonsterSenses s, Vec3 goal, long tick) {
        Vec3 pos = s.position();
        if (pos.distanceTo(goal) <= def.attackRange && def.hostile && tick >= attackReadyAt) {
            attackReadyAt = tick + def.attackCooldownTicks;
            enter(MonsterState.ATTACK, tick);
            return new MonsterIntent(MonsterState.ATTACK, goal, 0, goal, def.animation("attack"), target, null,
                    def.sounds.get("attack"), false);
        }
        return move(s, goal, speedOverride != null ? speedOverride : def.chaseSpeed, "walk", tick, null);
    }

    private MonsterIntent ambusher(MonsterSenses s, List<PlayerView> visible, long tick) {
        Vec3 pos = s.position();
        double radius = def.param("ambush_radius", 4);
        switch (state) {
            case AMBUSH_WAIT -> {
                for (PlayerView p : visible) {
                    if (p.position().distanceTo(pos) <= radius) {
                        target = p.id();
                        lastKnown = p.position();
                        lastSeenTick = tick;
                        enter(MonsterState.INTRO, tick);
                        return new MonsterIntent(MonsterState.INTRO, null, 0, p.eyePosition(), def.animation("ambush"),
                                null, null, def.sounds.get("intro"), false);
                    }
                }
                return MonsterIntent.idle(state, def.animation("hidden"));
            }
            case INTRO -> {
                if (tick - stateSince < (long) def.param("intro_ticks", 20)) {
                    return MonsterIntent.idle(state, def.animation("ambush"));
                }
                enter(MonsterState.CHASE, tick);
                lockedChaseUntil = tick + (long) def.param("chase_duration", 200);
            }
            case RETREAT -> {
                if (pos.distanceTo(home) <= 1.0) {
                    enter(MonsterState.AMBUSH_WAIT, tick);
                    return MonsterIntent.idle(state, def.animation("hidden"));
                }
                return move(s, home, def.walkSpeed * 1.5, "walk", tick, null);
            }
            default -> { }
        }
        if (!chaseLocked && tick > lockedChaseUntil && state != MonsterState.RETREAT) {
            enter(MonsterState.RETREAT, tick);
            return move(s, home, def.walkSpeed * 1.5, "walk", tick, null);
        }
        PlayerView seen = TargetSelector.pick(visible, policy, pos, target, seed);
        if (seen != null) {
            lastKnown = seen.position();
            lastSeenTick = tick;
            return pursue(s, seen.position(), tick, false);
        }
        if (lastKnown != null && tick - lastSeenTick <= def.memoryTicks) return pursue(s, lastKnown, tick, false);
        enter(MonsterState.RETREAT, tick);
        return move(s, home, def.walkSpeed * 1.5, "walk", tick, null);
    }

    private MonsterIntent npc(MonsterSenses s, long tick) {
        PlayerView nearest = TargetSelector.pick(s.players(), TargetPolicy.NEAREST, s.position(), null, seed);
        if (nearest != null && nearest.position().distanceTo(s.position()) < def.param("talk_range", 6)) {
            return new MonsterIntent(MonsterState.IDLE, null, 0, nearest.eyePosition(), def.animation("idle"), null, null, null, false);
        }
        return followRoute(s, tick, def.walkSpeed);
    }

    // ------------------------------------------------------------------ routes & movement

    private MonsterIntent followRoute(MonsterSenses s, long tick, double speed) {
        if (route.isEmpty()) {
            if (state != MonsterState.IDLE && def.behaviour != BehaviourProfile.AMBUSHER) enter(MonsterState.IDLE, tick);
            if (home != null && s.position().distanceTo(home) > 2.0 && def.behaviour != BehaviourProfile.NPC) {
                return move(s, home, def.walkSpeed, "walk", tick, null);
            }
            return look(s, "idle");
        }
        if (state != MonsterState.PATROL) enter(MonsterState.PATROL, tick);
        if (tick < waitUntil) return look(s, "idle");
        Vec3 goal = route.get(routeIndex);
        if (s.position().distanceTo(goal) <= 1.2) {
            advanceRoute();
            waitUntil = tick + (long) def.param("patrol_wait", 20);
            return look(s, "idle");
        }
        return move(s, goal, speed, def.behaviour == BehaviourProfile.CRAWLER ? "crawl" : "walk", tick, null);
    }

    private MonsterIntent followChaseRoute(MonsterSenses s, long tick) {
        if (chaseRoute.isEmpty()) {
            if (lastKnown != null) return pursue(s, lastKnown, tick, false);
            return look(s, "search");
        }
        if (chaseRouteIndex >= chaseRoute.size()) return look(s, "search");
        Vec3 goal = chaseRoute.get(chaseRouteIndex);
        if (s.position().distanceTo(goal) <= 1.5) {
            chaseRouteIndex++;
            return look(s, "search");
        }
        double speed = speedOverride != null ? speedOverride : def.chaseSpeed;
        return move(s, goal, speed, "run", tick, null);
    }

    private void advanceRoute() {
        if (route.isEmpty()) return;
        routeIndex++;
        if (routeIndex >= route.size()) routeIndex = routeLoop ? 0 : route.size() - 1;
    }

    /** Builds a movement intent and runs stuck detection/recovery for the goal. */
    private MonsterIntent move(MonsterSenses s, Vec3 goal, double baseSpeed, String anim, long tick, Id sound) {
        Vec3 pos = s.position();
        double dist = pos.distanceTo(goal);
        if (trackedGoal == null || trackedGoal.distanceTo(goal) > 2.0) {
            trackedGoal = goal;
            bestGoalDistance = dist;
            progressSince = tick;
        } else if (dist < bestGoalDistance - 0.5) {
            bestGoalDistance = dist;
            progressSince = tick;
        } else if (tick - progressSince > def.stuckTicks) {
            progressSince = tick;
            bestGoalDistance = dist;
            MonsterIntent recovered = recover(s, goal, tick);
            if (recovered != null) return recovered;
        }
        double speed = baseSpeed * difficulty;
        return new MonsterIntent(state, goal, speed, goal, def.animation(anim), null, null, sound, false);
    }

    private MonsterIntent recover(MonsterSenses s, Vec3 goal, long tick) {
        warn("stuck near " + fmt(s.position()) + " heading to " + fmt(goal) + " (" + recovery + ")");
        return switch (recovery) {
            case TELEPORT -> teleportIfUnseen(s, goal);
            case SKIP_WAYPOINT -> {
                if (chaseLocked && !chaseRoute.isEmpty() && chaseRouteIndex < chaseRoute.size()) {
                    chaseRouteIndex++;
                    yield null;
                }
                if (state == MonsterState.PATROL && !route.isEmpty()) {
                    advanceRoute();
                    yield null;
                }
                MonsterIntent t = teleportIfUnseen(s, goal);
                yield t != null ? t : MonsterIntent.idle(state, def.animation("search"));
            }
            case RETURN_TO_ROUTE -> {
                if (!route.isEmpty()) {
                    routeIndex = nearestIndex(route, s.position());
                    enter(MonsterState.PATROL, tick);
                }
                yield null;
            }
            case WAIT -> MonsterIntent.idle(state, def.animation("idle"));
            case DESPAWN -> {
                enter(MonsterState.DESPAWNED, tick);
                yield new MonsterIntent(state, null, 0, null, def.animation("despawn"), null, null, null, true);
            }
        };
    }

    private MonsterIntent teleportIfUnseen(MonsterSenses s, Vec3 goal) {
        for (PlayerView p : s.players()) {
            if (s.hasLineOfSight(p.eyePosition(), goal) || s.hasLineOfSight(p.eyePosition(), s.position())) return null;
        }
        trackedGoal = null;
        return new MonsterIntent(state, goal, 0, goal, def.animation("idle"), null, goal, null, false);
    }

    private MonsterIntent look(MonsterSenses s, String anim) {
        return new MonsterIntent(state, null, 0, null, def.animation(anim), null, null, null, false);
    }

    private Vec3 wanderPoint(Vec3 centre, long tick) {
        long slot = (tick / 60) + seed;
        double angle = (slot * 2.399963) % (Math.PI * 2);
        double radius = 3 + (slot % 3);
        return new Vec3(centre.x() + Math.cos(angle) * radius, centre.y(), centre.z() + Math.sin(angle) * radius);
    }

    private void enter(MonsterState next, long tick) {
        if (next != state) {
            state = next;
            stateSince = tick;
            if (next != MonsterState.CHASE && next != MonsterState.ATTACK) alerted = false;
        }
    }

    private void warn(String message) {
        if (warnings.size() >= MAX_WARNINGS) warnings.remove(0);
        warnings.add(message);
    }

    private static int nearestIndex(List<Vec3> points, Vec3 from) {
        if (points.isEmpty() || from == null) return 0;
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            double d = points.get(i).distanceSq(from);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static String fmt(Vec3 v) {
        return String.format("%.0f,%.0f,%.0f", v.x(), v.y(), v.z());
    }
}

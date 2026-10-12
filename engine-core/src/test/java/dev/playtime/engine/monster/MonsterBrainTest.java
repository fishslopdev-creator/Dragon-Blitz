package dev.playtime.engine.monster;

import dev.playtime.engine.config.TargetPolicy;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MonsterBrainTest {
    static final class Senses implements MonsterSenses {
        Vec3 pos = Vec3.ZERO;
        Vec3 facing = new Vec3(1, 0, 0);
        final List<PlayerView> players = new ArrayList<>();
        boolean los = true;

        @Override public Vec3 position() { return pos; }
        @Override public Vec3 facing() { return facing; }
        @Override public List<PlayerView> players() { return players; }
        @Override public boolean hasLineOfSight(Vec3 from, Vec3 to) { return los; }
    }

    static PlayerView player(UUID id, Vec3 pos, Vec3 look) {
        return new PlayerView(id, pos, pos.add(new Vec3(0, 1.6, 0)), look, 20, false, false, false, false);
    }

    static MonsterDefinition def(BehaviourProfile b) {
        MonsterDefinition d = new MonsterDefinition();
        d.id = Id.of("test_" + b.name().toLowerCase());
        d.behaviour = b;
        d.thinkInterval = 1;
        return d.normalize();
    }

    private final UUID alex = UUID.randomUUID();

    @Test
    void stalkerChasesWhatItSeesAndSearchesWhenItLosesSight() {
        Senses s = new Senses();
        MonsterBrain brain = new MonsterBrain(def(BehaviourProfile.STALKER), 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        s.players.add(player(alex, new Vec3(10, 0, 0), new Vec3(-1, 0, 0)));
        MonsterIntent i = brain.think(s, 0);
        assertEquals(MonsterState.CHASE, i.state());
        assertEquals(new Vec3(10, 0, 0), i.moveTarget());
        assertTrue(i.speed() > def(BehaviourProfile.STALKER).walkSpeed);

        s.players.clear();
        s.pos = new Vec3(10, 0, 0);
        long t = 1;
        MonsterIntent last = null;
        for (; t < 400; t++) last = brain.think(s, t);
        assertNotEquals(MonsterState.CHASE, last.state());
        assertTrue(last.state() == MonsterState.PATROL || last.state() == MonsterState.IDLE || last.state() == MonsterState.SEARCH);
    }

    @Test
    void playersBehindTheMonsterOrBehindWallsAreNotSeen() {
        Senses s = new Senses();
        MonsterBrain brain = new MonsterBrain(def(BehaviourProfile.STALKER), 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        s.players.add(player(alex, new Vec3(-10, 0, 0), new Vec3(1, 0, 0)));
        assertNotEquals(MonsterState.CHASE, brain.think(s, 0).state());
        s.players.clear();
        s.players.add(player(alex, new Vec3(10, 0, 0), new Vec3(-1, 0, 0)));
        s.los = false;
        assertNotEquals(MonsterState.CHASE, brain.think(s, 1).state());
    }

    @Test
    void attacksInRangeWithCooldown() {
        Senses s = new Senses();
        MonsterBrain brain = new MonsterBrain(def(BehaviourProfile.STALKER), 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        s.players.add(player(alex, new Vec3(1.5, 0, 0), new Vec3(-1, 0, 0)));
        MonsterIntent first = brain.think(s, 0);
        assertEquals(alex, first.attackTarget());
        assertNull(brain.think(s, 1).attackTarget(), "cooldown");
        assertEquals(alex, brain.think(s, 40).attackTarget());
    }

    @Test
    void investigatesNoises() {
        Senses s = new Senses();
        MonsterDefinition d = def(BehaviourProfile.SOUND_HUNTER);
        MonsterBrain brain = new MonsterBrain(d, 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        brain.hear(new Noise(new Vec3(0, 0, 12), 1.0, Id.of("alarm"), null));
        MonsterIntent i = brain.think(s, 0);
        assertEquals(MonsterState.INVESTIGATE, i.state());
        assertEquals(new Vec3(0, 0, 12), i.moveTarget());
    }

    @Test
    void soundHunterIsNearlyBlind() {
        Senses s = new Senses();
        MonsterBrain brain = new MonsterBrain(def(BehaviourProfile.SOUND_HUNTER), 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        s.players.add(player(alex, new Vec3(15, 0, 0), new Vec3(-1, 0, 0)));
        assertNotEquals(MonsterState.CHASE, brain.think(s, 0).state());
    }

    @Test
    void stealthRetreatsWhenWatchedAndAdvancesWhenNot() {
        Senses s = new Senses();
        MonsterBrain brain = new MonsterBrain(def(BehaviourProfile.STEALTH), 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        s.players.add(player(alex, new Vec3(10, 0, 0), new Vec3(-1, -0.1, 0)));
        MonsterIntent watched = brain.think(s, 0);
        assertEquals(MonsterState.RETREAT, watched.state());
        assertTrue(watched.moveTarget().x() < 0, "moves away from the watcher");
        s.players.set(0, player(alex, new Vec3(10, 0, 0), new Vec3(1, 0, 0)));
        MonsterIntent unseen = brain.think(s, 1);
        assertEquals(MonsterState.CHASE, unseen.state());
        assertTrue(unseen.moveTarget().x() > 0);
    }

    @Test
    void ambusherWaitsSpringsAndReturnsHome() {
        Senses s = new Senses();
        MonsterDefinition d = def(BehaviourProfile.AMBUSHER);
        d.params.put("chase_duration", 30.0);
        MonsterBrain brain = new MonsterBrain(d, 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        s.players.add(player(alex, new Vec3(12, 0, 0), new Vec3(-1, 0, 0)));
        assertEquals(MonsterState.AMBUSH_WAIT, brain.think(s, 0).state(), "too far away");
        s.players.set(0, player(alex, new Vec3(3, 0, 0), new Vec3(-1, 0, 0)));
        assertEquals(MonsterState.INTRO, brain.think(s, 1).state());
        MonsterIntent chase = brain.think(s, 30);
        assertTrue(chase.state() == MonsterState.CHASE || chase.state() == MonsterState.ATTACK);
        s.players.clear();
        s.pos = new Vec3(8, 0, 0);
        MonsterIntent back = brain.think(s, 400);
        assertEquals(MonsterState.RETREAT, back.state());
        assertEquals(Vec3.ZERO, back.moveTarget());
        s.pos = Vec3.ZERO;
        assertEquals(MonsterState.AMBUSH_WAIT, brain.think(s, 401).state());
    }

    @Test
    void npcsNeverAttack() {
        Senses s = new Senses();
        MonsterBrain brain = new MonsterBrain(def(BehaviourProfile.NPC), 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        s.players.add(player(alex, new Vec3(1, 0, 0), new Vec3(-1, 0, 0)));
        for (int t = 0; t < 50; t++) assertNull(brain.think(s, t).attackTarget());
    }

    @Test
    void stuckMonsterTeleportsOnlyWhenUnseen() {
        Senses s = new Senses();
        MonsterDefinition d = def(BehaviourProfile.STALKER);
        d.recovery = RecoveryStrategy.TELEPORT;
        d.stuckTicks = 20;
        MonsterBrain brain = new MonsterBrain(d, 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        brain.setRoute(List.of(new Vec3(30, 0, 0), new Vec3(0, 0, 0)), true);
        MonsterIntent teleport = null;
        for (int t = 0; t < 80 && teleport == null; t++) {
            MonsterIntent i = brain.think(s, t);
            if (i.teleportTo() != null) teleport = i;
        }
        assertNotNull(teleport, "a monster that cannot progress recovers instead of freezing");
        assertFalse(brain.warnings().isEmpty());
    }

    @Test
    void chaseLockFollowsRouteWhenTargetHidden() {
        Senses s = new Senses();
        MonsterBrain brain = new MonsterBrain(def(BehaviourProfile.SCRIPTED), 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        brain.beginChase(alex, List.of(new Vec3(5, 0, 0), new Vec3(10, 0, 0)), 0.5, 0);
        MonsterIntent i = brain.think(s, 0);
        assertEquals(new Vec3(5, 0, 0), i.moveTarget());
        assertEquals(0.5, i.speed(), 1e-9);
        brain.endChase(1);
        assertNotEquals(MonsterState.CHASE, brain.think(s, 2).state());
    }

    @Test
    void flareStunsOnlyVulnerableMonsters() {
        MonsterDefinition d = def(BehaviourProfile.SWARM);
        d.stunnedBy.add(Id.of("flare"));
        MonsterBrain brain = new MonsterBrain(d, 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        assertTrue(brain.stun(Id.of("flare"), 0));
        assertEquals(MonsterState.STUNNED, brain.think(new Senses(), 1).state());
        MonsterBrain boss = new MonsterBrain(def(BehaviourProfile.BOSS), 1.0, TargetPolicy.NEAREST, Vec3.ZERO, 1);
        assertFalse(boss.stun(Id.of("flare"), 0));
    }

    @Test
    void targetPolicies() {
        UUID b = UUID.randomUUID();
        var near = player(alex, new Vec3(2, 0, 0), Vec3.ZERO);
        var far = new PlayerView(b, new Vec3(9, 0, 0), new Vec3(9, 1.6, 0), Vec3.ZERO, 3, false, false, true, false);
        assertEquals(alex, TargetSelector.pick(List.of(near, far), TargetPolicy.NEAREST, Vec3.ZERO, null, 0).id());
        assertEquals(b, TargetSelector.pick(List.of(near, far), TargetPolicy.LOWEST_HEALTH, Vec3.ZERO, null, 0).id());
        assertEquals(b, TargetSelector.pick(List.of(near, far), TargetPolicy.HOST, Vec3.ZERO, null, 0).id());
        assertEquals(b, TargetSelector.pick(List.of(near, far), TargetPolicy.NEAREST, Vec3.ZERO, b, 0).id());
    }
}

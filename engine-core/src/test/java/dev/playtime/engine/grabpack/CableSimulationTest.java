package dev.playtime.engine.grabpack;

import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CableSimulationTest {
    static final class World implements CableWorld {
        boolean loaded = true;
        boolean clear = true;
        TargetInfo override;
        boolean gone;

        @Override public boolean isLoaded(Vec3 position) { return loaded; }
        @Override public boolean isLineClear(Vec3 from, Vec3 to) { return clear; }
        @Override public TargetInfo refresh(TargetInfo target) { return gone ? null : override != null ? override : target; }
    }

    private static List<CableEvent.Type> run(CableSimulation sim, Vec3 origin, World w, int ticks, long start) {
        List<CableEvent.Type> out = new ArrayList<>();
        for (int i = 0; i < ticks; i++) for (CableEvent e : sim.tick(origin, w, start + i, 10)) out.add(e.type());
        return out;
    }

    @Test
    void firesAttachesAndRetracts() {
        CableSimulation sim = new CableSimulation(new CableSettings());
        TargetInfo lever = TargetInfo.device(TargetKind.LEVER, new Vec3(10, 0, 0), Id.of("lever"));
        assertEquals(CableEvent.Type.FIRED, sim.fire(Vec3.ZERO, null, lever, 0).type());
        World w = new World();
        assertTrue(run(sim, Vec3.ZERO, w, 6, 1).contains(CableEvent.Type.ATTACHED));
        assertEquals(CableState.ATTACHED, sim.state());
        sim.release();
        assertTrue(run(sim, Vec3.ZERO, w, 6, 10).contains(CableEvent.Type.RETRACTED));
        assertEquals(CableState.IDLE, sim.state());
        assertTrue(sim.isOnCooldown(16));
        assertEquals(CableEvent.Type.REJECTED, sim.fire(Vec3.ZERO, null, lever, 16).type());
    }

    @Test
    void outOfRangeTargetFallsShort() {
        CableSettings s = new CableSettings();
        s.maxLength = 5;
        CableSimulation sim = new CableSimulation(s);
        sim.fire(Vec3.ZERO, null, TargetInfo.device(TargetKind.LEVER, new Vec3(50, 0, 0), Id.of("far")), 0);
        List<CableEvent.Type> events = run(sim, Vec3.ZERO, new World(), 10, 1);
        assertTrue(events.contains(CableEvent.Type.MISSED));
        assertFalse(events.contains(CableEvent.Type.ATTACHED));
    }

    @Test
    void vanishedTargetSnapsTheCable() {
        CableSimulation sim = new CableSimulation(new CableSettings());
        sim.fire(Vec3.ZERO, null, TargetInfo.entity(TargetKind.PULLABLE, new Vec3(4, 0, 0), "toy"), 0);
        World w = new World();
        run(sim, Vec3.ZERO, w, 3, 1);
        assertEquals(CableState.ATTACHED, sim.state());
        w.gone = true;
        assertTrue(run(sim, Vec3.ZERO, w, 1, 5).contains(CableEvent.Type.SNAPPED));
        assertEquals(CableState.RETRACTING, sim.state());
    }

    @Test
    void walkingAwaySnapsAndUnloadedChunksAreSafe() {
        CableSimulation sim = new CableSimulation(new CableSettings());
        sim.fire(Vec3.ZERO, null, TargetInfo.device(TargetKind.HANDLE, new Vec3(5, 0, 0), Id.of("h")), 0);
        World w = new World();
        run(sim, Vec3.ZERO, w, 4, 1);
        assertTrue(run(sim, new Vec3(-40, 0, 0), w, 1, 6).contains(CableEvent.Type.SNAPPED));

        CableSimulation sim2 = new CableSimulation(new CableSettings());
        sim2.fire(Vec3.ZERO, null, TargetInfo.device(TargetKind.HANDLE, new Vec3(20, 0, 0), Id.of("h")), 0);
        w.loaded = false;
        assertTrue(run(sim2, Vec3.ZERO, w, 1, 1).contains(CableEvent.Type.MISSED));
    }

    @Test
    void obstructionAndAttachTimeoutRelease() {
        CableSettings s = new CableSettings();
        s.maxAttachTicks = 5;
        s.obstructionGraceTicks = 2;
        CableSimulation sim = new CableSimulation(s);
        sim.fire(Vec3.ZERO, null, TargetInfo.device(TargetKind.LEVER, new Vec3(2, 0, 0), Id.of("l")), 0);
        World w = new World();
        run(sim, Vec3.ZERO, w, 2, 1);
        assertEquals(CableState.ATTACHED, sim.state());
        assertTrue(run(sim, Vec3.ZERO, w, 8, 3).contains(CableEvent.Type.RELEASED), "timeout");

        CableSimulation blocked = new CableSimulation(new CableSettings());
        blocked.fire(Vec3.ZERO, null, TargetInfo.device(TargetKind.LEVER, new Vec3(10, 0, 0), Id.of("l")), 0);
        w.clear = false;
        assertTrue(run(blocked, Vec3.ZERO, w, 1, 1).contains(CableEvent.Type.MISSED));
    }

    @Test
    void movingTargetIsFollowed() {
        CableSimulation sim = new CableSimulation(new CableSettings());
        sim.fire(Vec3.ZERO, null, TargetInfo.entity(TargetKind.PULLABLE, new Vec3(6, 0, 0), "toy"), 0);
        World w = new World();
        w.override = TargetInfo.entity(TargetKind.PULLABLE, new Vec3(6, 0, 3), "toy");
        run(sim, Vec3.ZERO, w, 5, 1);
        assertEquals(CableState.ATTACHED, sim.state());
        assertEquals(new Vec3(6, 0, 3), sim.handPosition());
    }

    @Test
    void selfPullIsBounded() {
        Vec3 v = SelfPull.velocity(Vec3.ZERO, new Vec3(100, 0, 0), 0.9);
        assertTrue(v.length() <= 0.9 + 1e-9);
        assertEquals(Vec3.ZERO, SelfPull.velocity(Vec3.ZERO, new Vec3(1, 0, 0), 0.9));
    }
}

package dev.playtime.engine.grabpack;

import dev.playtime.engine.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side state of one launcher's cable. The hand travels at a fixed speed, attaches
 * when it reaches its target, and always ends by retracting to the launcher. Every abnormal
 * situation (target gone, chunk unloaded, line blocked, player too far, attached too long)
 * leads to a retract, so a hand can never stay attached to something invalid.
 */
public final class CableSimulation {
    private final CableSettings settings;
    private CableState state = CableState.IDLE;
    private Vec3 handPos = Vec3.ZERO;
    private TargetInfo target;
    private Vec3 aimPoint;
    private int attachedTicks;
    private int obstructedTicks;
    private long cooldownUntil;

    public CableSimulation(CableSettings settings) {
        this.settings = settings;
    }

    public CableState state() { return state; }

    public Vec3 handPosition() { return handPos; }

    public TargetInfo target() { return target; }

    public boolean isBusy() { return state != CableState.IDLE; }

    public boolean isOnCooldown(long tick) { return tick < cooldownUntil; }

    public double maxLength() { return settings.maxLength; }

    /**
     * Fires towards {@code target} (or {@code aim} on a miss). Range and cooldown are
     * checked here; target compatibility is checked by the caller against the hand definition.
     */
    public CableEvent fire(Vec3 origin, Vec3 aim, TargetInfo target, long tick) {
        if (state != CableState.IDLE) return new CableEvent(CableEvent.Type.REJECTED, target, "busy");
        if (tick < cooldownUntil) return new CableEvent(CableEvent.Type.REJECTED, target, "cooldown");
        Vec3 point = target != null ? target.position() : aim;
        if (point == null) return new CableEvent(CableEvent.Type.REJECTED, null, "no aim");
        if (origin.distanceTo(point) > settings.maxLength) {
            // Fire anyway to show the cable falling short, then retract.
            this.target = null;
            this.aimPoint = origin.add(point.subtract(origin).normalize().scale(settings.maxLength));
        } else {
            this.target = target;
            this.aimPoint = point;
        }
        this.handPos = origin;
        this.state = CableState.FIRING;
        this.attachedTicks = 0;
        this.obstructedTicks = 0;
        return new CableEvent(CableEvent.Type.FIRED, target, this.target == null && target != null ? "out of range" : null);
    }

    public CableEvent startPull() {
        if (state != CableState.ATTACHED) return new CableEvent(CableEvent.Type.REJECTED, target, "not attached");
        state = CableState.PULLING;
        return new CableEvent(CableEvent.Type.PULL_STARTED, target, null);
    }

    public CableEvent release() {
        if (state == CableState.IDLE || state == CableState.RETRACTING) return null;
        TargetInfo old = target;
        beginRetract();
        return new CableEvent(CableEvent.Type.RELEASED, old, null);
    }

    /** Immediate reset (player disconnected, died, or changed dimension). */
    public void cancel(long tick, int cooldownTicks) {
        state = CableState.IDLE;
        target = null;
        aimPoint = null;
        cooldownUntil = tick + cooldownTicks;
    }

    /** Advances one server tick. {@code origin} is the launcher position this tick. */
    public List<CableEvent> tick(Vec3 origin, CableWorld world, long tick, int cooldownTicks) {
        List<CableEvent> events = new ArrayList<>(1);
        switch (state) {
            case IDLE -> { }
            case FIRING -> tickFiring(origin, world, events);
            case ATTACHED, PULLING -> tickAttached(origin, world, events);
            case RETRACTING -> {
                handPos = handPos.moveTowards(origin, settings.retractSpeed);
                if (handPos.distanceSq(origin) < 0.01) {
                    state = CableState.IDLE;
                    cooldownUntil = tick + cooldownTicks;
                    events.add(new CableEvent(CableEvent.Type.RETRACTED, null, null));
                }
            }
        }
        return events;
    }

    private void tickFiring(Vec3 origin, CableWorld world, List<CableEvent> events) {
        if (target != null) {
            TargetInfo fresh = world.refresh(target);
            if (fresh == null) {
                events.add(new CableEvent(CableEvent.Type.MISSED, target, "target vanished"));
                beginRetract();
                return;
            }
            target = fresh;
            aimPoint = fresh.position();
        }
        Vec3 next = handPos.moveTowards(aimPoint, settings.fireSpeed);
        if (!world.isLoaded(next)) {
            events.add(new CableEvent(CableEvent.Type.MISSED, target, "unloaded"));
            beginRetract();
            return;
        }
        if (next.distanceTo(aimPoint) > 1.0e-6 && !world.isLineClear(handPos, next)) {
            events.add(new CableEvent(CableEvent.Type.MISSED, target, "obstructed"));
            beginRetract();
            return;
        }
        handPos = next;
        if (origin.distanceTo(handPos) > settings.maxLength * settings.snapFactor) {
            events.add(new CableEvent(CableEvent.Type.SNAPPED, target, "too far"));
            beginRetract();
            return;
        }
        if (handPos.distanceSq(aimPoint) < 1.0e-6) {
            if (target == null) {
                events.add(new CableEvent(CableEvent.Type.MISSED, null, "nothing to grab"));
                beginRetract();
            } else {
                state = CableState.ATTACHED;
                attachedTicks = 0;
                events.add(new CableEvent(CableEvent.Type.ATTACHED, target, null));
            }
        }
    }

    private void tickAttached(Vec3 origin, CableWorld world, List<CableEvent> events) {
        attachedTicks++;
        TargetInfo fresh = world.refresh(target);
        if (fresh == null || !world.isLoaded(fresh.position())) {
            events.add(new CableEvent(CableEvent.Type.SNAPPED, target, "target lost"));
            beginRetract();
            return;
        }
        target = fresh;
        handPos = fresh.position();
        if (origin.distanceTo(handPos) > settings.maxLength * settings.snapFactor) {
            events.add(new CableEvent(CableEvent.Type.SNAPPED, target, "too far"));
            beginRetract();
            return;
        }
        if (!world.isLineClear(origin, handPos)) {
            if (++obstructedTicks > settings.obstructionGraceTicks) {
                events.add(new CableEvent(CableEvent.Type.SNAPPED, target, "obstructed"));
                beginRetract();
                return;
            }
        } else {
            obstructedTicks = 0;
        }
        if (settings.maxAttachTicks > 0 && attachedTicks > settings.maxAttachTicks) {
            events.add(new CableEvent(CableEvent.Type.RELEASED, target, "timeout"));
            beginRetract();
        }
    }

    private void beginRetract() {
        state = CableState.RETRACTING;
        target = null;
        aimPoint = null;
        obstructedTicks = 0;
    }
}

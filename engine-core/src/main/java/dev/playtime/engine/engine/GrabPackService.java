package dev.playtime.engine.engine;

import dev.playtime.engine.access.AccessRule;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.grabpack.CableEvent;
import dev.playtime.engine.grabpack.CableSettings;
import dev.playtime.engine.grabpack.CableSimulation;
import dev.playtime.engine.grabpack.CableState;
import dev.playtime.engine.grabpack.CableWorld;
import dev.playtime.engine.grabpack.GrabPackDefinition;
import dev.playtime.engine.grabpack.SelfPull;
import dev.playtime.engine.grabpack.TargetInfo;
import dev.playtime.engine.grabpack.TargetKind;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.hand.HandAbilities;
import dev.playtime.engine.hand.HandStats;
import dev.playtime.engine.hand.LauncherSide;
import dev.playtime.engine.logic.Action;
import dev.playtime.engine.logic.ActionType;
import dev.playtime.engine.monster.Noise;
import dev.playtime.engine.progress.PlayerProgress;
import dev.playtime.engine.puzzle.PuzzleEvent;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Json;
import dev.playtime.engine.util.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative GrabPack. The client only asks to fire, pull or release; the game
 * layer raycasts on the server to find the target, and this service decides what the hand
 * may do with it.
 */
public final class GrabPackService {
    private final PlaytimeEngine e;
    private final Map<UUID, Pack> packs = new HashMap<>();

    private static final class Pack {
        final Map<LauncherSide, CableSimulation> cables = new EnumMap<>(LauncherSide.class);
        final Map<LauncherSide, HandStats> firedWith = new EnumMap<>(LauncherSide.class);
        /** Power source a conducting hand is holding, per launcher. */
        final Map<LauncherSide, Id> holdingSource = new EnumMap<>(LauncherSide.class);
        long chargedUntil;
        final Map<Id, Integer> usesLeft = new HashMap<>();
        final Map<Id, Long> rechargeAt = new HashMap<>();
    }

    GrabPackService(PlaytimeEngine engine) {
        this.e = engine;
    }

    public record FireResult(boolean fired, String message, List<HandEffect> effects) {}

    private Pack pack(UUID player) {
        return packs.computeIfAbsent(player, k -> new Pack());
    }

    private CableSimulation cable(UUID player, LauncherSide side, HandStats stats) {
        Pack p = pack(player);
        CableSimulation sim = p.cables.get(side);
        if (sim != null && (sim.isBusy() || sim.isOnCooldown(e.tick))) return sim;
        CableSettings settings = Json.copy(stats.definition().cable, CableSettings.class);
        settings.maxLength = Math.min(stats.range(), grabPackLength(player));
        CableSimulation fresh = new CableSimulation(settings);
        p.cables.put(side, fresh);
        return fresh;
    }

    private double grabPackLength(UUID player) {
        PlayerProgress prog = e.progression.progressOrCreate(player);
        return e.content.<GrabPackDefinition>get(ContentKind.GRABPACKS, prog.grabPack)
                .map(g -> g.cableLength).orElse(24.0);
    }

    /**
     * Fire request. {@code target} comes from a server-side raycast (null = nothing hit,
     * {@code aim} is the end of the ray).
     */
    public FireResult fire(UUID player, LauncherSide side, Vec3 origin, Vec3 aim, TargetInfo target) {
        PlayerProgress prog = e.progression.progressOrCreate(player);
        List<HandEffect> effects = new ArrayList<>();
        if (prog.grabPack == null) return new FireResult(false, "You don't have a GrabPack.", effects);
        Optional<HandStats> statsOpt = e.handService.selectedStats(player, side);
        if (statsOpt.isEmpty()) return new FireResult(false, "No hand on that launcher.", effects);
        HandStats stats = statsOpt.get();
        Pack pack = pack(player);
        Id handId = stats.definition().id;
        if (stats.charges() > 0) {
            Long rechargeAt = pack.rechargeAt.get(handId);
            if (rechargeAt != null && e.tick >= rechargeAt) {
                pack.usesLeft.remove(handId);
                pack.rechargeAt.remove(handId);
            }
            int left = pack.usesLeft.getOrDefault(handId, stats.charges());
            if (left <= 0) return new FireResult(false, stats.definition().displayName + " is recharging.", effects);
        }
        CableSimulation sim = cable(player, side, stats);
        if (sim.isBusy()) return new FireResult(false, null, effects);
        if (sim.isOnCooldown(e.tick)) return new FireResult(false, null, effects);

        TargetInfo usable = target;
        String message = null;
        if (target != null && !stats.canTarget(target.kind()) && !stats.has(HandAbilities.FLARE)) {
            usable = null;
            message = "The " + stats.definition().displayName + " can't grab that.";
            effects.add(HandEffect.at(HandEffect.Type.INVALID, player, target.position()));
        }
        if (target != null && origin.distanceTo(target.position()) > sim.maxLength()) {
            message = "Out of range.";
            effects.add(HandEffect.at(HandEffect.Type.INVALID, player, target.position()));
        }
        CableEvent fired = sim.fire(origin, aim, usable, e.tick);
        if (fired.type() == CableEvent.Type.REJECTED) return new FireResult(false, null, effects);
        pack.firedWith.put(side, stats);
        if (stats.charges() > 0) {
            int left = pack.usesLeft.getOrDefault(handId, stats.charges()) - 1;
            pack.usesLeft.put(handId, left);
            if (left <= 0) pack.rechargeAt.put(handId, e.tick + stats.definition().rechargeTicks);
        }
        e.director.hear(new Noise(origin, 0.5, Id.of("grabpack_fire"), player));
        return new FireResult(true, message, effects);
    }

    public CableEvent pull(UUID player, LauncherSide side) {
        CableSimulation sim = pack(player).cables.get(side);
        return sim == null ? null : sim.startPull();
    }

    public void release(UUID player, LauncherSide side) {
        Pack p = pack(player);
        CableSimulation sim = p.cables.get(side);
        if (sim != null) sim.release();
        p.holdingSource.remove(side);
    }

    /** Disconnect, death, dimension change: drop everything immediately. */
    public void cancelAll(UUID player) {
        Pack p = packs.remove(player);
        if (p != null) for (CableSimulation c : p.cables.values()) c.cancel(e.tick, 0);
    }

    public boolean isCharged(UUID player) {
        Pack p = packs.get(player);
        return p != null && p.chargedUntil > e.tick;
    }

    /**
     * Advances the player's cables. Called by the game layer once per tick per player with
     * a GrabPack, passing launcher positions and world queries.
     */
    public List<HandEffect> tick(UUID player, Vec3 leftOrigin, Vec3 rightOrigin, CableWorld world) {
        Pack p = packs.get(player);
        List<HandEffect> effects = new ArrayList<>();
        if (p == null) return effects;
        for (Map.Entry<LauncherSide, CableSimulation> en : p.cables.entrySet()) {
            LauncherSide side = en.getKey();
            CableSimulation sim = en.getValue();
            if (!sim.isBusy()) continue;
            Vec3 origin = side == LauncherSide.LEFT ? leftOrigin : rightOrigin;
            HandStats stats = p.firedWith.get(side);
            int cooldown = stats == null ? 10 : stats.cooldownTicks();
            for (CableEvent ev : sim.tick(origin, world, e.tick, cooldown)) {
                if (stats == null) continue;
                switch (ev.type()) {
                    case ATTACHED -> onAttached(player, side, stats, ev.target(), origin, effects);
                    case MISSED -> onMissed(player, stats, sim.handPosition(), effects);
                    case SNAPPED, RELEASED -> p.holdingSource.remove(side);
                    default -> { }
                }
            }
            if (sim.state() == CableState.PULLING && sim.target() != null) {
                onPulling(player, stats, sim.target(), origin, effects);
            }
        }
        return effects;
    }

    public List<CableSnapshot> snapshot(UUID player) {
        Pack p = packs.get(player);
        List<CableSnapshot> out = new ArrayList<>();
        if (p == null) return out;
        for (Map.Entry<LauncherSide, CableSimulation> en : p.cables.entrySet()) {
            HandStats stats = p.firedWith.get(en.getKey());
            out.add(new CableSnapshot(player, en.getKey(), stats == null ? null : stats.definition().id,
                    en.getValue().state(), en.getValue().handPosition(), p.chargedUntil > e.tick));
        }
        return out;
    }

    // ---------------------------------------------------------------- ability resolution

    private void onMissed(UUID player, HandStats stats, Vec3 at, List<HandEffect> effects) {
        if (stats.has(HandAbilities.FLARE)) flare(player, stats, at, effects);
        if (stats.has(HandAbilities.PULSE)) pulse(player, stats, at, effects);
    }

    private void onAttached(UUID player, LauncherSide side, HandStats stats, TargetInfo target, Vec3 origin,
                            List<HandEffect> effects) {
        Pack pack = pack(player);
        if (stats.has(HandAbilities.FLARE)) flare(player, stats, target.position(), effects);
        if (stats.has(HandAbilities.PULSE)) pulse(player, stats, target.position(), effects);

        Device device = target.device() == null ? null : e.graph.device(target.device()).orElse(null);
        if (device != null) {
            if (!e.mayInteract(player, device)) {
                e.bridge.showStatus(player, "You can't use that.");
                return;
            }
            switch (device.type) {
                case HAND_SCANNER -> scan(player, stats, device);
                case GENERATOR, CHARGING_STATION, BATTERY_DEVICE -> {
                    if (stats.has(HandAbilities.CONDUCT) && e.power.isPowered(device.id)) {
                        pack.holdingSource.put(side, device.id);
                        effects.add(new HandEffect(HandEffect.Type.ARC, player, target.position(), null, null, 0, 0));
                    }
                    if (stats.has(HandAbilities.STORE_CHARGE) && e.power.isPowered(device.id)) {
                        pack.chargedUntil = e.tick + (long) stats.definition().param("charge_ticks", 200);
                        effects.add(new HandEffect(HandEffect.Type.CHARGED, player, null, null, null,
                                (int) stats.definition().param("charge_ticks", 200), 0));
                    }
                }
                case POWER_NODE, POWER_RECEIVER, CIRCUIT_ENDPOINT, MACHINE -> {
                    boolean conducting = stats.has(HandAbilities.CONDUCT) && holdsPower(pack);
                    boolean charged = stats.has(HandAbilities.STORE_CHARGE) && pack.chargedUntil > e.tick;
                    if (conducting || charged) {
                        if (charged) pack.chargedUntil = 0;
                        e.signals.setActive(device.id, true, player);
                        e.power.chargeTemporarily(device.id, e.tick + (long) stats.definition().param("power_ticks", 1200));
                        effects.add(new HandEffect(HandEffect.Type.ARC, player, target.position(), null, null, 0, 0));
                    } else if (stats.has(HandAbilities.CONDUCT) || stats.has(HandAbilities.STORE_CHARGE)) {
                        e.bridge.showStatus(player, "The hand needs a charge first.");
                    }
                }
                case LEVER, POWER_SWITCH -> {
                    if (stats.has(HandAbilities.GRAB) || stats.has(HandAbilities.INTERACT)) e.useDevice(player, device.id, null);
                }
                case BUTTON, TIMED_SWITCH -> {
                    if (stats.has(HandAbilities.INTERACT) || stats.has(HandAbilities.GRAB)) e.useDevice(player, device.id, null);
                }
                case LAUNCH_PAD -> {
                    Id needed = device.idSetting("hand_tag");
                    boolean ok = stats.has(HandAbilities.LAUNCH_PAD) && (needed == null || stats.interactions().contains(needed));
                    if (ok && (!"true".equals(device.setting("requires_power", "false")) || e.power.isPowered(device.id))) {
                        e.signals.pulse(device.id, player);
                        double up = device.intSetting("strength", 12) / 10.0;
                        effects.add(new HandEffect(HandEffect.Type.LAUNCH_PLAYER, player, device.pos.center(),
                                new Vec3(0, up, 0), null, 0, up));
                        e.puzzles.dispatch(PuzzleEvent.reached(device.id, player));
                    } else {
                        e.bridge.showStatus(player, "Nothing happens.");
                    }
                }
                case ATTACH_POINT -> {
                    if (stats.has(HandAbilities.SELF_PULL) && e.config.rules.allowSelfPull) {
                        effects.add(new HandEffect(HandEffect.Type.SELF_PULL, player, target.position(),
                                SelfPull.velocity(origin, target.position(), e.config.rules.maxSelfPullSpeed), null, 0, 0));
                    }
                    e.puzzles.dispatch(PuzzleEvent.reached(device.id, player));
                }
                case PULSE_RECEIVER -> { /* handled by pulse() when the hand has PULSE */ }
                case HAND_PICKUP -> e.useDevice(player, device.id, null);
                default -> {
                    if (stats.has(HandAbilities.INTERACT)) e.useDevice(player, device.id, null);
                }
            }
            return;
        }
        switch (target.kind()) {
            case PULLABLE, COLLECTIBLE -> {
                if (stats.has(HandAbilities.PULL) || stats.has(HandAbilities.RETRIEVE)) {
                    effects.add(new HandEffect(HandEffect.Type.PULL_ENTITY, player, target.position(), null,
                            target.entityRef(), 0, stats.definition().param("pull_strength", 0.6)));
                }
            }
            case BREAKABLE -> {
                if (stats.has(HandAbilities.FLARE) && stats.interactions().contains(Id.of("flammable"))) {
                    effects.add(new HandEffect(HandEffect.Type.BURN, player, target.position(), null, target.entityRef(), 60, 0));
                }
                if (stats.has(HandAbilities.PRESSURE_BLAST)) {
                    effects.add(new HandEffect(HandEffect.Type.PRESSURE_BLAST, player, target.position(),
                            target.position().subtract(origin).normalize(), target.entityRef(),
                            (int) stats.definition().param("charge_ticks", 30), stats.definition().param("blast_strength", 1.5)));
                }
            }
            default -> { }
        }
    }

    private void onPulling(UUID player, HandStats stats, TargetInfo target, Vec3 origin, List<HandEffect> effects) {
        if (target.kind() == TargetKind.PULLABLE && stats.has(HandAbilities.PULL)) {
            effects.add(new HandEffect(HandEffect.Type.PULL_ENTITY, player, target.position(),
                    origin.subtract(target.position()).normalize().scale(stats.definition().param("pull_strength", 0.6)),
                    target.entityRef(), 0, 0));
        }
        if (target.kind() == TargetKind.ATTACH_POINT && stats.has(HandAbilities.SELF_PULL) && e.config.rules.allowSelfPull) {
            effects.add(new HandEffect(HandEffect.Type.SELF_PULL, player, target.position(),
                    SelfPull.velocity(origin, target.position(), e.config.rules.maxSelfPullSpeed), null, 0, 0));
        }
    }

    private boolean holdsPower(Pack pack) {
        for (Id source : pack.holdingSource.values()) if (e.power.isPowered(source)) return true;
        return false;
    }

    private void scan(UUID player, HandStats stats, Device scanner) {
        AccessRule rule = e.scannerRule(scanner);
        var result = e.access.evaluate(rule, e.accessRequest(player, stats, scanner.id));
        e.puzzles.dispatch(PuzzleEvent.scan(scanner.id, stats.definition().id, stats.interactions(), player));
        if (result.granted()) {
            e.signals.pulse(scanner.id, player);
            if (scanner.latch) e.signals.setActive(scanner.id, true, player);
            Id door = scanner.idSetting("door");
            if (door != null) e.openDoorFor(player, door);
            e.bridge.playSound(null, Id.of("scanner.accept"), scanner.pos, 1f);
        } else {
            e.bridge.showStatus(player, result.message());
            e.bridge.playSound(null, Id.of("scanner.deny"), scanner.pos, 1f);
        }
    }

    private void flare(UUID player, HandStats stats, Vec3 at, List<HandEffect> effects) {
        int ticks = (int) stats.definition().param("flare_light_ticks", 400);
        effects.add(new HandEffect(HandEffect.Type.LIGHT, player, at, null, null, ticks, 15));
        e.director.stunNear(at, stats.definition().param("scare_radius", 6), HandAbilities.FLARE);
    }

    private void pulse(UUID player, HandStats stats, Vec3 at, List<HandEffect> effects) {
        double radius = stats.definition().param("pulse_radius", 5);
        long powerTicks = (long) stats.definition().param("pulse_power_ticks", 100);
        effects.add(new HandEffect(HandEffect.Type.PULSE_RING, player, at, null, null, 10, radius));
        int hit = 0;
        for (Device d : e.graph.devicesOfType(DeviceType.PULSE_RECEIVER)) {
            if (d.pos == null || d.pos.center().distanceTo(at) > radius) continue;
            if (!e.mayInteract(player, d) || e.graph.state(d.id).disabled) continue;
            hit++;
            // A pulsed receiver reports the pulse, then stays active (and powered) for a short window.
            // Downstream links see ON then OFF, so "both receivers at once" circuits work as expected.
            e.devicePulsed(d, player);
            e.signals.setActive(d.id, true, player);
            e.power.chargeTemporarily(d.id, e.tick + powerTicks);
            Action off = Action.of(ActionType.SET_DEVICE, d.id);
            off.on = false;
            off.delay = (int) Math.max(1, powerTicks);
            e.actions.run(List.of(off), player);
            e.bridge.playSound(null, Id.of("pulse.receiver"), d.pos, 1f);
        }
        if (hit == 0) e.bridge.showStatus(player, "The pulse fades. Nothing here responds to it.");
    }
}

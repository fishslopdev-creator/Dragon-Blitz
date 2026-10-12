package dev.playtime.fabric.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.playtime.engine.engine.CableSnapshot;
import dev.playtime.engine.engine.GrabPackService;
import dev.playtime.engine.engine.HandEffect;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.grabpack.CableState;
import dev.playtime.engine.grabpack.CableWorld;
import dev.playtime.engine.grabpack.TargetInfo;
import dev.playtime.engine.grabpack.TargetKind;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.hand.HandStats;
import dev.playtime.engine.hand.LauncherSide;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;
import dev.playtime.fabric.Ids;
import dev.playtime.fabric.PlaytimeMod;
import dev.playtime.fabric.block.EngineBlock;
import dev.playtime.fabric.block.EngineBlockEntity;
import dev.playtime.fabric.entity.PlaytimeMonster;
import dev.playtime.fabric.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Minecraft side of the GrabPack. The client only says "fire left" or "release right";
 * the server ray-casts from the player's eyes, builds the target, lets the engine decide,
 * then performs the resulting effects and broadcasts cable positions for rendering.
 */
public final class ServerGrabPack {
    public static final TagKey<Block> PRESSURE_BREAKABLE = TagKey.create(Registries.BLOCK, PlaytimeMod.id("pressure_breakable"));
    public static final TagKey<Block> FLAMMABLE = TagKey.create(Registries.BLOCK, PlaytimeMod.id("flammable"));

    private static final Set<UUID> ACTIVE = new HashSet<>();

    private ServerGrabPack() {}

    public static void request(ServerPlayer player, PlaytimeEngine engine, String action, LauncherSide side) {
        if (player.isSpectator() || !player.isAlive()) return;
        UUID id = player.getUUID();
        switch (action) {
            case "fire" -> fire(player, engine, side);
            case "pull" -> engine.grabPack().pull(id, side);
            case "release" -> engine.grabPack().release(id, side);
            default -> { }
        }
        ACTIVE.add(id);
    }

    private static void fire(ServerPlayer player, PlaytimeEngine engine, LauncherSide side) {
        HandStats stats = engine.hands().selectedStats(player.getUUID(), side).orElse(null);
        double range = stats == null ? 24 : stats.range();
        ServerLevel level = player.level();
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        net.minecraft.world.phys.Vec3 limit = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB box = player.getBoundingBox().expandTowards(limit.subtract(eye)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, limit, box,
                e -> !e.isSpectator() && e.isPickable() && e != player, eye.distanceToSqr(limit));

        TargetInfo target = null;
        if (entityHit != null) {
            target = entityTarget(entityHit.getEntity());
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            target = blockTarget(level, blockHit);
        }
        Vec3 origin = launcher(player, side);
        GrabPackService.FireResult r = engine.grabPack().fire(player.getUUID(), side, origin, Ids.vec(limit), target);
        if (r.message() != null) player.displayClientMessage(Component.literal(r.message()), true);
        apply(player, engine, r.effects());
        if (r.fired()) {
            Id sound = stats == null ? null : stats.definition().sounds.get("fire");
            playAt(level, player.getX(), player.getEyeY(), player.getZ(), sound != null ? sound : Id.of("grabpack.fire"));
        }
    }

    private static TargetInfo entityTarget(Entity e) {
        Vec3 pos = Ids.vec(e.getBoundingBox().getCenter());
        TargetKind kind = e instanceof ItemEntity ? TargetKind.COLLECTIBLE
                : e instanceof Player || e instanceof PlaytimeMonster ? TargetKind.SURFACE : TargetKind.PULLABLE;
        return TargetInfo.entity(kind, pos, e.getUUID().toString());
    }

    private static TargetInfo blockTarget(ServerLevel level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof EngineBlock block && level.getBlockEntity(pos) instanceof EngineBlockEntity be) {
            Id device = EngineBlock.ensureDevice(level, pos);
            if (device == null) device = be.deviceId();
            return TargetInfo.device(kindOf(block.deviceType()), Ids.vec(net.minecraft.world.phys.Vec3.atCenterOf(pos)), device);
        }
        if (state.is(PRESSURE_BREAKABLE) || state.is(FLAMMABLE)) {
            return new TargetInfo(TargetKind.BREAKABLE, Ids.vec(net.minecraft.world.phys.Vec3.atCenterOf(pos)), null, pos.toShortString());
        }
        return TargetInfo.surface(Ids.vec(hit.getLocation()));
    }

    static TargetKind kindOf(DeviceType type) {
        return switch (type) {
            case HAND_SCANNER -> TargetKind.SCANNER;
            case LEVER, POWER_SWITCH -> TargetKind.LEVER;
            case BUTTON, TIMED_SWITCH -> TargetKind.BUTTON;
            case GENERATOR, BATTERY_DEVICE, CHARGING_STATION -> TargetKind.POWER_SOURCE;
            case POWER_NODE, POWER_RECEIVER, CIRCUIT_ENDPOINT, MACHINE -> TargetKind.POWER_RECEIVER;
            case LAUNCH_PAD -> TargetKind.LAUNCH_PAD;
            case ATTACH_POINT -> TargetKind.ATTACH_POINT;
            case PULSE_RECEIVER -> TargetKind.PULSE_RECEIVER;
            case HAND_PICKUP -> TargetKind.COLLECTIBLE;
            default -> TargetKind.INTERACTIVE;
        };
    }

    /** Launcher muzzle: just below the eyes, offset to the player's left or right. */
    static Vec3 launcher(Player player, LauncherSide side) {
        double yaw = Math.toRadians(player.getYRot());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double s = side == LauncherSide.LEFT ? -0.35 : 0.35;
        return new Vec3(player.getX() + rx * s, player.getEyeY() - 0.35, player.getZ() + rz * s);
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(MinecraftServer server, PlaytimeEngine engine) {
        if (ACTIVE.isEmpty()) return;
        for (UUID id : List.copyOf(ACTIVE)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive()) {
                engine.grabPack().cancelAll(id);
                ACTIVE.remove(id);
                continue;
            }
            List<HandEffect> effects = engine.grabPack().tick(id, launcher(player, LauncherSide.LEFT),
                    launcher(player, LauncherSide.RIGHT), new World(player.level(), engine));
            apply(player, engine, effects);
            List<CableSnapshot> cables = engine.grabPack().snapshot(id);
            boolean busy = cables.stream().anyMatch(c -> c.state() != CableState.IDLE);
            broadcast(player, cables, busy);
            if (!busy) ACTIVE.remove(id);
        }
    }

    private static void broadcast(ServerPlayer owner, List<CableSnapshot> cables, boolean busy) {
        JsonObject o = new JsonObject();
        o.addProperty("player", owner.getUUID().toString());
        JsonArray arr = new JsonArray();
        if (busy) {
            for (CableSnapshot c : cables) {
                if (c.state() == CableState.IDLE) continue;
                JsonObject j = new JsonObject();
                j.addProperty("side", c.side().name().toLowerCase());
                j.addProperty("hand", c.hand() == null ? "" : c.hand().toString());
                j.addProperty("x", c.handPosition().x());
                j.addProperty("y", c.handPosition().y());
                j.addProperty("z", c.handPosition().z());
                j.addProperty("state", c.state().name().toLowerCase());
                arr.add(j);
            }
        }
        o.add("cables", arr);
        Payloads.S2C payload = Payloads.S2C.of("cables", o);
        for (ServerPlayer p : owner.level().players()) {
            if (p.distanceToSqr(owner) < 96 * 96) ServerPlayNetworking.send(p, payload);
        }
    }

    // ------------------------------------------------------------------ effects

    static void apply(ServerPlayer player, PlaytimeEngine engine, List<HandEffect> effects) {
        if (effects == null || effects.isEmpty()) return;
        ServerLevel level = player.level();
        for (HandEffect e : effects) {
            try {
                applyOne(player, level, e);
            } catch (RuntimeException ex) {
                PlaytimeMod.LOG.warn("Hand effect {} failed: {}", e.type(), ex.toString());
            }
        }
    }

    private static void applyOne(ServerPlayer player, ServerLevel level, HandEffect e) {
        Vec3 p = e.position();
        switch (e.type()) {
            case PULL_ENTITY -> {
                Entity target = entity(level, e.entityRef());
                if (target == null || e.vector() == null) return;
                target.setDeltaMovement(Ids.mc(e.vector()));
                target.hurtMarked = true;
            }
            case SELF_PULL, LAUNCH_PLAYER -> {
                if (e.vector() == null) return;
                player.setDeltaMovement(Ids.mc(e.vector()));
                player.hurtMarked = true;
                player.fallDistance = 0;
            }
            case LIGHT -> {
                EngineHolder.bridge().tempLight(level, BlockPos.containing(p.x(), p.y(), p.z()), Math.max(20, e.ticks()));
                level.sendParticles(ParticleTypes.FLAME, p.x(), p.y(), p.z(), 12, 0.2, 0.2, 0.2, 0.02);
            }
            case PRESSURE_BLAST -> {
                Entity target = entity(level, e.entityRef());
                if (target != null && e.vector() != null) {
                    target.setDeltaMovement(Ids.mc(e.vector().scale(e.strength())));
                    target.hurtMarked = true;
                } else {
                    BlockPos pos = BlockPos.containing(p.x(), p.y(), p.z());
                    if (level.getBlockState(pos).is(PRESSURE_BREAKABLE)) level.destroyBlock(pos, false, player);
                }
                level.sendParticles(ParticleTypes.CLOUD, p.x(), p.y(), p.z(), 16, 0.3, 0.3, 0.3, 0.1);
            }
            case BURN -> {
                BlockPos pos = BlockPos.containing(p.x(), p.y(), p.z());
                if (level.getBlockState(pos).is(FLAMMABLE) && level.getBlockState(pos.above()).isAir()) {
                    level.setBlock(pos.above(), BaseFireBlock.getState(level, pos.above()), Block.UPDATE_ALL);
                }
            }
            case PULSE_RING -> {
                double r = e.strength();
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    level.sendParticles(ParticleTypes.GLOW, p.x() + Math.cos(a) * r, p.y(), p.z() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                playAt(level, p.x(), p.y(), p.z(), Id.of("pulse_hand.pulse"));
            }
            case ARC -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x(), p.y(), p.z(), 10, 0.2, 0.2, 0.2, 0.05);
            case CHARGED -> player.displayClientMessage(Component.literal("Hand charged"), true);
            case INVALID -> level.sendParticles(ParticleTypes.SMOKE, p.x(), p.y(), p.z(), 6, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private static Entity entity(ServerLevel level, String ref) {
        if (ref == null) return null;
        try {
            return level.getEntity(UUID.fromString(ref));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static void playAt(ServerLevel level, double x, double y, double z, Id sound) {
        if (sound == null) return;
        Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent(Ids.mc(sound)));
        level.playSound(null, x, y, z, holder, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** World queries for the cable simulation. */
    private record World(ServerLevel level, PlaytimeEngine engine) implements CableWorld {
        @Override
        public boolean isLoaded(Vec3 position) {
            return level.isLoaded(BlockPos.containing(position.x(), position.y(), position.z()));
        }

        @Override
        public boolean isLineClear(Vec3 from, Vec3 to) {
            BlockHitResult hit = level.clip(new ClipContext(Ids.mc(from), Ids.mc(to), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(Ids.mc(to)) < 1.2;
        }

        @Override
        public TargetInfo refresh(TargetInfo target) {
            if (target.device() != null) {
                return engine.graph().device(target.device()).isPresent() ? target : null;
            }
            if (target.entityRef() != null && target.kind() != TargetKind.BREAKABLE) {
                Entity e = entity(level, target.entityRef());
                if (e == null || !e.isAlive()) return null;
                return new TargetInfo(target.kind(), Ids.vec(e.getBoundingBox().getCenter()), null, target.entityRef());
            }
            return target;
        }
    }
}

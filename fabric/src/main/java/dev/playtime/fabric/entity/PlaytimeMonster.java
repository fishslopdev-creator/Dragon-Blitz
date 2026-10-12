package dev.playtime.fabric.entity;

import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.monster.MonsterIntent;
import dev.playtime.engine.monster.MonsterSenses;
import dev.playtime.engine.monster.PlayerView;
import dev.playtime.engine.util.Vec3;
import dev.playtime.fabric.Ids;
import dev.playtime.fabric.server.EngineHolder;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single entity type behind every engine monster and NPC. Which character it is, its
 * size and its current animation are synced to clients; all decisions come from the
 * engine's MonsterDirector. Not saved with the world: encounters respawn from their triggers.
 */
public class PlaytimeMonster extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> MONSTER = SynchedEntityData.defineId(PlaytimeMonster.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ANIMATION = SynchedEntityData.defineId(PlaytimeMonster.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(PlaytimeMonster.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(PlaytimeMonster.class, EntityDataSerializers.FLOAT);
    private static final Map<String, RawAnimation> ANIMATIONS = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PlaytimeMonster(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void configure(String monsterId, float width, float height) {
        entityData.set(MONSTER, monsterId);
        entityData.set(WIDTH, Math.max(0.3F, width));
        entityData.set(HEIGHT, Math.max(0.3F, height));
        refreshDimensions();
    }

    /** Engine monster id, e.g. {@code playtime:the_playwright}. */
    public String monsterId() {
        return entityData.get(MONSTER);
    }

    public String animationName() {
        return entityData.get(ANIMATION);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MONSTER, "");
        builder.define(ANIMATION, "idle");
        builder.define(WIDTH, 0.8F);
        builder.define(HEIGHT, 2.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (WIDTH.equals(accessor) || HEIGHT.equals(accessor)) refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return EntityDimensions.scalable(entityData.get(WIDTH), entityData.get(HEIGHT));
    }

    @Override
    protected void registerGoals() {
        // No vanilla goals: the engine's MonsterBrain decides everything.
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.isCreativePlayer() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) {
            discard();
            return;
        }
        MonsterIntent intent = engine.director().think(getUUID(), new Senses(level));
        if (intent == null) {
            discard();
            return;
        }
        apply(level, engine, intent);
    }

    private void apply(ServerLevel level, PlaytimeEngine engine, MonsterIntent intent) {
        if (intent.despawn()) {
            discard();
            return;
        }
        if (intent.animation() != null && !intent.animation().equals(animationName())) entityData.set(ANIMATION, intent.animation());
        if (intent.teleportTo() != null) {
            Vec3 t = intent.teleportTo();
            teleportTo(t.x(), t.y(), t.z());
            getNavigation().stop();
        }
        if (intent.moveTarget() != null && intent.speed() > 0) {
            Vec3 t = intent.moveTarget();
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.min(1.0, intent.speed()));
            getNavigation().moveTo(t.x(), t.y(), t.z(), 1.0);
        } else {
            getNavigation().stop();
        }
        if (intent.lookAt() != null) {
            Vec3 l = intent.lookAt();
            getLookControl().setLookAt(l.x(), l.y(), l.z());
        }
        if (intent.sound() != null) {
            Holder<SoundEvent> sound = Holder.direct(SoundEvent.createVariableRangeEvent(Ids.mc(intent.sound())));
            level.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, 1.5F, 1.0F);
        }
        if (intent.attackTarget() != null) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(intent.attackTarget());
            if (target != null && target.level() == level && target.distanceTo(this) < 6) {
                swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                engine.director().onAttack(getUUID(), target.getUUID());
            }
        }
    }

    /** What this monster can perceive, read from the live world. */
    private final class Senses implements MonsterSenses {
        private final ServerLevel level;
        private List<PlayerView> players;

        Senses(ServerLevel level) {
            this.level = level;
        }

        @Override
        public Vec3 position() {
            return Ids.vec(PlaytimeMonster.this.position());
        }

        @Override
        public Vec3 facing() {
            net.minecraft.world.phys.Vec3 look = PlaytimeMonster.this.getLookAngle();
            return new Vec3(look.x, 0, look.z).normalize();
        }

        @Override
        public List<PlayerView> players() {
            if (players != null) return players;
            players = new ArrayList<>();
            UUIDHolder.host = EngineHolder.engine().config().multiplayer.hostPlayer;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.isCreative() || p.distanceToSqr(PlaytimeMonster.this) > 64 * 64) continue;
                net.minecraft.world.phys.Vec3 look = p.getLookAngle();
                players.add(new PlayerView(p.getUUID(), Ids.vec(p.position()), Ids.vec(p.getEyePosition()),
                        new Vec3(look.x, look.y, look.z), p.getHealth(), p.isShiftKeyDown(), p.isSprinting(),
                        p.getUUID().equals(UUIDHolder.host), p.isInvisible()));
            }
            return players;
        }

        @Override
        public boolean hasLineOfSight(Vec3 from, Vec3 to) {
            HitResult hit = level.clip(new ClipContext(Ids.mc(from), Ids.mc(to), ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, PlaytimeMonster.this));
            return hit.getType() == HitResult.Type.MISS;
        }
    }

    private static final class UUIDHolder {
        static java.util.UUID host;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<PlaytimeMonster>("main", 4, test -> {
            String name = test.animatable().animationName();
            if (name == null || name.isEmpty()) return PlayState.STOP;
            RawAnimation anim = ANIMATIONS.computeIfAbsent(name, n -> RawAnimation.begin().thenLoop(n));
            return test.setAndContinue(anim);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

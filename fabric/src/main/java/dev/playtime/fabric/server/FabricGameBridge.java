package dev.playtime.fabric.server;

import com.google.gson.JsonObject;
import dev.playtime.engine.access.DoorDefinition;
import dev.playtime.engine.engine.GameBridge;
import dev.playtime.engine.engine.MenuPayload;
import dev.playtime.engine.engine.PlayerPosition;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceState;
import dev.playtime.engine.monster.MonsterDefinition;
import dev.playtime.engine.profile.ItemSpec;
import dev.playtime.engine.profile.StartingKit;
import dev.playtime.engine.story.CollectibleDefinition;
import dev.playtime.engine.story.DialogueSession;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Json;
import dev.playtime.fabric.Ids;
import dev.playtime.fabric.PlaytimeMod;
import dev.playtime.fabric.block.EngineBlock;
import dev.playtime.fabric.block.FactoryLightBlock;
import dev.playtime.fabric.block.ModBlocks;
import dev.playtime.fabric.entity.ModEntities;
import dev.playtime.fabric.entity.PlaytimeMonster;
import dev.playtime.fabric.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minecraft implementation of the engine's {@link GameBridge}. Everything here runs on the
 * server thread. Presentation calls become vanilla packets or Playtime payloads; world calls
 * change blocks and entities.
 */
public final class FabricGameBridge implements GameBridge {
    /** Hard cap on blocks touched by one lights/door call. */
    private static final int MAX_AREA = 32_768;

    private final MinecraftServer server;
    private final Map<UUID, Boolean> sprintDenied = new HashMap<>();
    private final List<TempLight> tempLights = new ArrayList<>();

    private record TempLight(ServerLevel level, BlockPos pos, long until) {}

    FabricGameBridge(MinecraftServer server) {
        this.server = server;
    }

    void shutdown() {
        for (TempLight t : tempLights) removeLight(t);
        tempLights.clear();
    }

    void tick() {
        long now = server.overworld().getGameTime();
        tempLights.removeIf(t -> {
            if (now < t.until) return false;
            removeLight(t);
            return true;
        });
        if (now % 2 == 0) {
            for (Map.Entry<UUID, Boolean> e : sprintDenied.entrySet()) {
                ServerPlayer p = player(e.getKey());
                if (p != null && e.getValue() && p.isSprinting()) p.setSprinting(false);
            }
        }
    }

    public ServerPlayer player(UUID id) {
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }

    private void send(UUID player, String kind, Object body) {
        ServerPlayer p = player(player);
        if (p != null) ServerPlayNetworking.send(p, Payloads.S2C.of(kind, body));
    }

    // ------------------------------------------------------------------ players

    @Override
    public Collection<UUID> onlinePlayers() {
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) out.add(p.getUUID());
        return out;
    }

    @Override
    public boolean isOperator(UUID player) {
        ServerPlayer p = player(player);
        return p != null && p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public String playerName(UUID player) {
        ServerPlayer p = player(player);
        return p != null ? p.getGameProfile().name() : String.valueOf(player);
    }

    @Override
    public PlayerPosition position(UUID player) {
        ServerPlayer p = player(player);
        if (p == null) return null;
        return new PlayerPosition(Ids.dimension(p.level()), p.getX(), p.getY(), p.getZ());
    }

    @Override
    public String team(UUID player) {
        ServerPlayer p = player(player);
        PlayerTeam team = p == null ? null : p.getTeam();
        return team == null ? null : team.getName();
    }

    // ------------------------------------------------------------------ presentation

    @Override
    public void showTitle(UUID player, String title, String subtitle, int ticks) {
        ServerPlayer p = player(player);
        if (p == null) return;
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, Math.max(20, ticks), 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle == null ? "" : subtitle)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title == null ? "" : title)));
    }

    @Override
    public void showStatus(UUID player, String message) {
        ServerPlayer p = player(player);
        if (p != null && message != null && !message.isEmpty()) {
            p.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(message)));
        }
    }

    @Override
    public void showObjective(UUID player, String text, int ticksLeft) {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        o.addProperty("ticks", ticksLeft);
        send(player, "objective", o);
    }

    @Override
    public void showUnlock(UUID player, String title, String description, Id icon) {
        JsonObject o = new JsonObject();
        o.addProperty("title", title);
        o.addProperty("description", description == null ? "" : description);
        if (icon != null) o.addProperty("icon", icon.toString());
        send(player, "unlock", o);
    }

    @Override
    public void openDialogue(UUID player, DialogueSession.View view) {
        send(player, "dialogue", Json.toTree(view));
    }

    @Override
    public void closeDialogue(UUID player) {
        send(player, "dialogue_close", new JsonObject());
    }

    @Override
    public void openMainMenu(UUID player, MenuPayload payload) {
        send(player, "menu", Json.toTree(payload));
    }

    @Override
    public void playRecording(UUID player, CollectibleDefinition c) {
        JsonObject o = new JsonObject();
        o.addProperty("title", c.title == null ? c.id.toString() : c.title);
        o.addProperty("text", c.text == null ? "" : c.text);
        o.addProperty("kind", c.kind.name().toLowerCase());
        send(player, "recording", o);
        if (c.audio != null) playSound(player, c.audio, null, 1.0F);
    }

    @Override
    public void showCredits(UUID player, Id credits) {
        JsonObject o = new JsonObject();
        var cfg = dev.playtime.fabric.server.EngineHolder.engine().config();
        o.addProperty("title", cfg.gameTitle);
        o.addProperty("subtitle", cfg.subtitle);
        send(player, "credits", o);
    }

    @Override
    public void playSound(UUID player, Id sound, BlockRef pos, float volume) {
        if (sound == null) return;
        Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent(Ids.mc(sound)));
        if (pos != null) {
            ServerLevel level = Ids.level(server, pos.dimension());
            level.playSound(null, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, holder, SoundSource.BLOCKS, volume, 1.0F);
            return;
        }
        ServerPlayer p = player(player);
        if (p != null) {
            p.connection.send(new ClientboundSoundPacket(holder, SoundSource.MASTER, p.getX(), p.getY(), p.getZ(),
                    volume, 1.0F, p.getRandom().nextLong()));
        }
    }

    @Override
    public void stopSound(UUID player, Id sound) {
        ServerPlayer p = player(player);
        if (p != null) p.connection.send(new ClientboundStopSoundPacket(sound == null ? null : Ids.mc(sound), null));
    }

    @Override
    public void particles(Id particle, BlockRef pos, int count) {
        if (pos == null) return;
        ServerLevel level = Ids.level(server, pos.dimension());
        ParticleOptions options = particleFor(particle);
        level.sendParticles(options, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, Math.min(count, 128), 0.4, 0.4, 0.4, 0.02);
    }

    static ParticleOptions particleFor(Id particle) {
        if (particle != null) {
            var type = BuiltInRegistries.PARTICLE_TYPE.getValue(Ids.mc(particle));
            if (type instanceof ParticleOptions simple) return simple;
            switch (particle.path()) {
                case "sparks" -> {
                    return ParticleTypes.ELECTRIC_SPARK;
                }
                case "red_smoke" -> {
                    return ParticleTypes.CAMPFIRE_COSY_SMOKE;
                }
                case "pulse_ring" -> {
                    return ParticleTypes.GLOW;
                }
                default -> { }
            }
        }
        return ParticleTypes.SMOKE;
    }

    @Override
    public void cameraShake(UUID player, int ticks, float strength) {
        JsonObject o = new JsonObject();
        o.addProperty("ticks", ticks);
        o.addProperty("strength", strength);
        send(player, "shake", o);
    }

    @Override
    public void screenFade(UUID player, int ticks) {
        JsonObject o = new JsonObject();
        o.addProperty("ticks", ticks);
        send(player, "fade", o);
    }

    // ------------------------------------------------------------------ world

    @Override
    public void setLights(Box area, boolean on) {
        ServerLevel level = Ids.level(server, area.dimension());
        if (area.volume() > MAX_AREA) {
            PlaytimeMod.LOG.warn("lights area too large ({} blocks); capped", area.volume());
        }
        int touched = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (++touched > MAX_AREA) break;
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (s.getBlock() instanceof FactoryLightBlock && s.getValue(FactoryLightBlock.LIT) != on) {
                level.setBlock(pos, s.setValue(FactoryLightBlock.LIT, on), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void deviceChanged(Device device, DeviceState state) {
        if (device.pos == null) return;
        ServerLevel level = Ids.level(server, device.pos.dimension());
        BlockPos pos = Ids.pos(device.pos);
        if (level.isLoaded(pos)) EngineBlock.showState(level, pos, device, state.active, state.powered);
    }

    /**
     * Doors are drawn as Factory Door blocks in front of their anchor (the Door Controller):
     * one block along {@code facing}, {@code width} wide (centred) and {@code height} tall.
     * Opening removes the door blocks; closing puts them back into empty space only.
     */
    @Override
    public void doorChanged(DoorDefinition door, boolean open) {
        if (door.anchor == null) return;
        ServerLevel level = Ids.level(server, door.anchor.dimension());
        Direction facing = Direction.byName(door.facing == null ? "north" : door.facing);
        if (facing == null || facing.getAxis().isVertical()) facing = Direction.NORTH;
        Direction across = facing.getClockWise();
        BlockPos front = Ids.pos(door.anchor).relative(facing);
        int half = (door.width - 1) / 2;
        for (int w = 0; w < door.width; w++) {
            for (int h = 0; h < door.height; h++) {
                BlockPos pos = front.relative(across, w - half).above(h);
                if (!level.isLoaded(pos)) continue;
                BlockState s = level.getBlockState(pos);
                if (open && s.is(ModBlocks.FACTORY_DOOR)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                } else if (!open && (s.isAir() || s.canBeReplaced())) {
                    level.setBlock(pos, ModBlocks.FACTORY_DOOR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    public void giveItem(UUID player, ItemSpec spec) {
        ServerPlayer p = player(player);
        if (p == null || spec == null || spec.item == null) return;
        Item item = BuiltInRegistries.ITEM.getValue(Ids.mc(spec.item));
        if (item == Items.AIR) {
            PlaytimeMod.LOG.warn("Unknown item {} in a give action", spec.item);
            return;
        }
        int left = Math.max(1, spec.count);
        while (left > 0) {
            int n = Math.min(left, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, n);
            if (!p.getInventory().add(stack)) p.drop(stack, false);
            left -= n;
        }
    }

    @Override
    public void applyStartingKit(UUID player, StartingKit kit) {
        ServerPlayer p = player(player);
        if (p == null) return;
        for (ItemSpec spec : kit.items()) giveItem(player, spec);
        for (Id key : kit.keyItems()) {
            if (BuiltInRegistries.ITEM.getValue(Ids.mc(key)) != Items.AIR && !hasItems(player, Set.of(key))) {
                giveItem(player, new ItemSpec(key, 1));
            }
        }
        sprintDenied.put(player, !kit.canSprint());
    }

    @Override
    public void consumeHeldItem(UUID player, Id item) {
        ServerPlayer p = player(player);
        if (p == null) return;
        ItemStack held = p.getMainHandItem();
        if (!held.isEmpty() && Ids.engine(BuiltInRegistries.ITEM.getKey(held.getItem())).equals(item)) held.shrink(1);
    }

    @Override
    public Set<Id> heldCredentials(UUID player) {
        ServerPlayer p = player(player);
        Set<Id> out = new HashSet<>();
        if (p == null) return out;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) out.add(Ids.engine(BuiltInRegistries.ITEM.getKey(s.getItem())));
        }
        return out;
    }

    @Override
    public boolean removeCredentialItem(UUID player, Id credential) {
        ServerPlayer p = player(player);
        if (p == null) return false;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && Ids.engine(BuiltInRegistries.ITEM.getKey(s.getItem())).equals(credential)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasItems(UUID player, Set<Id> items) {
        return heldCredentials(player).containsAll(items);
    }

    @Override
    public void teleport(UUID player, BlockRef pos, float yaw) {
        ServerPlayer p = player(player);
        if (p == null || pos == null) return;
        ServerLevel level = Ids.level(server, pos.dimension());
        p.teleportTo(level, pos.x() + 0.5, pos.y(), pos.z() + 0.5, Set.of(), yaw, 0.0F, true);
        p.fallDistance = 0;
    }

    @Override
    public UUID spawnMonster(MonsterDefinition def, BlockRef pos) {
        ServerLevel level = Ids.level(server, pos.dimension());
        PlaytimeMonster m = ModEntities.MONSTER.create(level, EntitySpawnReason.EVENT);
        if (m == null) return null;
        m.configure(def.id.toString(), def.width, def.height);
        m.setCustomName(Component.literal(def.displayName));
        m.setCustomNameVisible(false);
        m.snapTo(pos.x() + 0.5, pos.y(), pos.z() + 0.5, 0.0F, 0.0F);
        return level.addFreshEntity(m) ? m.getUUID() : null;
    }

    @Override
    public void despawnMonster(UUID entity) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(entity);
            if (e != null) {
                e.discard();
                return;
            }
        }
    }

    @Override
    public boolean isMonsterAlive(UUID entity) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(entity);
            if (e != null) return e.isAlive();
        }
        return false;
    }

    @Override
    public void damagePlayer(UUID player, UUID monster, float amount, boolean kill) {
        ServerPlayer p = player(player);
        if (p == null || p.isCreative() || p.isSpectator()) return;
        ServerLevel level = p.level();
        Entity attacker = level.getEntity(monster);
        var source = attacker instanceof net.minecraft.world.entity.LivingEntity le
                ? level.damageSources().mobAttack(le) : level.damageSources().generic();
        p.hurtServer(level, source, kill ? Float.MAX_VALUE : amount);
    }

    @Override
    public void syncPlayer(UUID player) {
        ServerPlayer p = player(player);
        if (p != null) Sync.sendPlayerState(p);
    }

    // ------------------------------------------------------------------ helpers

    /** Places a temporary light (flares) that is removed after {@code ticks}. */
    public void tempLight(ServerLevel level, BlockPos pos, int ticks) {
        BlockPos target = pos;
        if (!level.getBlockState(target).isAir()) target = target.above();
        if (!level.getBlockState(target).isAir()) return;
        level.setBlock(target, Blocks.LIGHT.defaultBlockState(), Block.UPDATE_ALL);
        tempLights.add(new TempLight(level, target, level.getGameTime() + ticks));
    }

    private static void removeLight(TempLight t) {
        if (t.level.getBlockState(t.pos).is(Blocks.LIGHT)) {
            t.level.setBlock(t.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    public static Identifier id(Id id) {
        return Ids.mc(id);
    }
}

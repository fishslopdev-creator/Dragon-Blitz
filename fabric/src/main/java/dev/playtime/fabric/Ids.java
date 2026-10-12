package dev.playtime.fabric;

import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Conversions between engine value types and Minecraft types. */
public final class Ids {
    private Ids() {}

    public static Identifier mc(Id id) {
        return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
    }

    public static Id engine(Identifier id) {
        return new Id(id.getNamespace(), id.getPath());
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    public static BlockRef ref(Level level, BlockPos pos) {
        return new BlockRef(dimension(level), pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos pos(BlockRef ref) {
        return new BlockPos(ref.x(), ref.y(), ref.z());
    }

    public static ServerLevel level(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        if (id == null) return server.overworld();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        return level != null ? level : server.overworld();
    }

    public static Vec3 vec(net.minecraft.world.phys.Vec3 v) {
        return new Vec3(v.x, v.y, v.z);
    }

    public static net.minecraft.world.phys.Vec3 mc(Vec3 v) {
        return new net.minecraft.world.phys.Vec3(v.x(), v.y(), v.z());
    }
}

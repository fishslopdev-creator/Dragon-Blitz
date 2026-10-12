package dev.playtime.fabric.server;

import dev.playtime.engine.access.DoorDefinition;
import dev.playtime.engine.admin.AdminResult;
import dev.playtime.engine.admin.AdminService;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import dev.playtime.fabric.Ids;
import dev.playtime.fabric.block.EngineBlock;
import dev.playtime.fabric.block.EngineBlockEntity;
import dev.playtime.fabric.block.FactoryLightBlock;
import dev.playtime.fabric.block.ModBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /playtime sample install}: installs the sample content through the engine, then
 * builds a simple factory shell and places the engine blocks so it can be played at once.
 * The facility extends about 50 blocks east of the player and 11 blocks to each side.
 */
public final class SampleBuilder {
    private static final int MIN_X = -3, MAX_X = 50, HALF_Z = 11, CEILING = 6;

    private SampleBuilder() {}

    public static boolean install(CommandSourceStack source, ServerPlayer player) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) return false;
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition().below();
        BlockRef ref = Ids.ref(level, origin);
        AdminResult r = new AdminService(engine).installSample(player.getUUID(), ref, true);
        for (String line : r.lines()) {
            if (r.ok()) source.sendSuccess(() -> Component.literal(line), false);
            else source.sendFailure(Component.literal(line));
        }
        if (!r.ok()) return false;

        buildShell(level, origin);
        for (Device d : engine.graph().devices()) {
            if (d.pos == null || !d.id.path().startsWith("sample/")) continue;
            EngineBlock block = ModBlocks.forType(d.type);
            if (block == null) continue;
            BlockPos pos = Ids.pos(d.pos);
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof EngineBlockEntity be) be.setDeviceId(d.id);
            if (pos.getY() > origin.getY() + 1) {
                for (BlockPos below = pos.below(); below.getY() > origin.getY(); below = below.below()) {
                    level.setBlock(below, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        for (DoorDefinition door : engine.doors().all()) {
            if (door.id.path().startsWith("sample/")) EngineHolder.bridge().doorChanged(door, engine.doors().isOpen(door.id));
        }
        engine.respawnAtCheckpoint(player.getUUID(), Id.parse("playtime:sample/start"));
        source.sendSuccess(() -> Component.literal(
                "Sample built. Switch to Survival or Adventure and choose New Game, or run /playtime menu."), false);
        return true;
    }

    private static void buildShell(ServerLevel level, BlockPos o) {
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState ceiling = Blocks.SMOOTH_BASALT.defaultBlockState();
        BlockState light = ModBlocks.FACTORY_LIGHT.defaultBlockState().setValue(FactoryLightBlock.LIT, true);
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = -HALF_Z; z <= HALF_Z; z++) {
                boolean edge = x == MIN_X || x == MAX_X || z == -HALF_Z || z == HALF_Z;
                level.setBlock(o.offset(x, 0, z), floor, Block.UPDATE_CLIENTS);
                boolean lamp = !edge && x % 6 == 0 && z % 6 == 0;
                level.setBlock(o.offset(x, CEILING, z), lamp ? light : ceiling, Block.UPDATE_CLIENTS);
                for (int y = 1; y < CEILING; y++) {
                    boolean divider = (x == 25 || x == 45) && y <= 5;
                    level.setBlock(o.offset(x, y, z), edge || divider ? wall : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // Doorways in the two dividers are filled by the doors themselves (closed = Factory Door blocks).
        for (int z = -1; z <= 1; z++) for (int y = 1; y <= 3; y++) level.setBlock(o.offset(25, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int z = 0; z <= 1; z++) for (int y = 1; y <= 3; y++) level.setBlock(o.offset(45, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        // A crawlspace shortcut from the shutdown room towards the breaker room.
        level.setBlock(o.offset(45, 1, 6), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
}

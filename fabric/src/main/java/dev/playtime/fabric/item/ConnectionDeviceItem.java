package dev.playtime.fabric.item;

import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.util.Id;
import dev.playtime.fabric.block.EngineBlock;
import dev.playtime.fabric.server.EngineHolder;
import dev.playtime.fabric.server.Editors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Links engine blocks. Right-click a source device, then a destination device; a screen
 * then asks for the link type, delay and target. Sneak-right-click clears the selection.
 * Operators only.
 */
public class ConnectionDeviceItem extends Item {
    private static final Map<UUID, Id> SOURCE = new HashMap<>();

    public ConnectionDeviceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) return InteractionResult.PASS;
        if (!EngineHolder.bridge().isOperator(player.getUUID())) {
            player.displayClientMessage(Component.literal("Only operators can use the Connection Device."), true);
            return InteractionResult.FAIL;
        }
        BlockState state = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (!(state.getBlock() instanceof EngineBlock)) {
            if (player.isShiftKeyDown()) {
                SOURCE.remove(player.getUUID());
                player.displayClientMessage(Component.literal("Connection cleared."), true);
            }
            return InteractionResult.PASS;
        }
        Id device = EngineBlock.ensureDevice(ctx.getLevel(), ctx.getClickedPos());
        if (device == null) return InteractionResult.FAIL;
        Id source = SOURCE.get(player.getUUID());
        if (source == null || player.isShiftKeyDown()) {
            SOURCE.put(player.getUUID(), device);
            int links = engine.graph().outgoing(device).size() + engine.graph().incoming(device).size();
            player.displayClientMessage(Component.literal("Source: " + device + " (" + links
                    + " existing links). Now right-click the destination."), true);
            Editors.showLinks(player, device);
            return InteractionResult.SUCCESS;
        }
        SOURCE.remove(player.getUUID());
        if (source.equals(device)) {
            player.displayClientMessage(Component.literal("Pick a different block as the destination."), true);
            return InteractionResult.FAIL;
        }
        Editors.openConnectionEditor(player, source, device);
        return InteractionResult.SUCCESS;
    }
}

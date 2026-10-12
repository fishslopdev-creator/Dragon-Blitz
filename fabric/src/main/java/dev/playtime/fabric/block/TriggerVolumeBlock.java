package dev.playtime.fabric.block;

import dev.playtime.engine.graph.DeviceType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible, walk-through trigger. Survival players can't see or touch it; in Creative it
 * can be selected (outline shape) so map makers can configure or break it.
 */
public class TriggerVolumeBlock extends EngineBlock {
    public TriggerVolumeBlock(Properties properties) {
        super(DeviceType.TRIGGER_VOLUME, false, properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean bl) {
        if (!level.isClientSide() && entity instanceof ServerPlayer sp && !sp.isSpectator()) enter(level, pos, sp);
    }
}

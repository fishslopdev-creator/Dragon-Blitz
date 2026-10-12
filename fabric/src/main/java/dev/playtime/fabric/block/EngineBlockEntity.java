package dev.playtime.fabric.block;

import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.util.Id;
import dev.playtime.fabric.Ids;
import dev.playtime.fabric.server.EngineHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Remembers which engine device this block is. Removing the block removes the device. */
public class EngineBlockEntity extends BlockEntity {
    private String deviceId = "";

    public EngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ENGINE_BLOCK_ENTITY, pos, state);
    }

    public Id deviceId() {
        return Id.tryParse(deviceId);
    }

    public void setDeviceId(Id id) {
        this.deviceId = id == null ? "" : id.toString();
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("device", deviceId);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        deviceId = in.getStringOr("device", "");
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine != null && level != null && !level.isClientSide()) {
            engine.interactions().breakAt(Ids.ref(level, pos));
        }
    }
}

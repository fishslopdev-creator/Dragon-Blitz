package dev.playtime.fabric.block;

import com.mojang.serialization.MapCodec;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.util.Id;
import dev.playtime.fabric.Ids;
import dev.playtime.fabric.item.ConnectionDeviceItem;
import dev.playtime.fabric.server.EngineHolder;
import dev.playtime.fabric.server.Editors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A placed engine device (generator, reader, scanner, trigger...). The block only forwards
 * to the engine: placement registers a device, use and stepping report interactions, and
 * the ACTIVE/POWERED properties mirror the engine's device state for visuals.
 */
public class EngineBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    private final DeviceType deviceType;
    private final boolean stepTrigger;
    private final MapCodec<EngineBlock> codec;
    /** Per player+block cooldown so stepping triggers fire at most once a second. */
    private static final Map<String, Long> STEP_COOLDOWN = new HashMap<>();

    public EngineBlock(DeviceType type, boolean stepTrigger, Properties properties) {
        super(properties);
        this.deviceType = type;
        this.stepTrigger = stepTrigger;
        this.codec = simpleCodec(p -> new EngineBlock(type, stepTrigger, p));
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(POWERED, false));
    }

    public DeviceType deviceType() {
        return deviceType;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EngineBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) ensureDevice(level, pos);
    }

    /** Registers the device with the engine if needed and returns its id. */
    public static @Nullable Id ensureDevice(Level level, BlockPos pos) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null || !(level.getBlockEntity(pos) instanceof EngineBlockEntity be)) return null;
        if (!(level.getBlockState(pos).getBlock() instanceof EngineBlock block)) return null;
        Id existing = be.deviceId();
        if (existing != null && engine.graph().device(existing).isPresent()) return existing;
        Id id = engine.interactions().place(block.deviceType, Ids.ref(level, pos), existing, DeviceDefaults.of(block.deviceType));
        be.setDeviceId(id);
        return id;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || stack.getItem() instanceof ConnectionDeviceItem) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return interact(level, pos, player, stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player, ItemStack.EMPTY);
    }

    private InteractionResult interact(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        PlaytimeEngine engine = EngineHolder.engine();
        Id id = ensureDevice(level, pos);
        if (engine == null || id == null) return InteractionResult.PASS;
        if (sp.isShiftKeyDown() && sp.isCreative() && EngineHolder.bridge().isOperator(sp.getUUID())) {
            Editors.openDeviceEditor(sp, id);
            return InteractionResult.SUCCESS;
        }
        if (deviceType == DeviceType.SETUP_CONSOLE) {
            Editors.openSetupConsole(sp);
            return InteractionResult.SUCCESS;
        }
        Id held = stack.isEmpty() ? null : Ids.engine(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        engine.useDevice(sp.getUUID(), id, held);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (stepTrigger && !level.isClientSide() && entity instanceof ServerPlayer sp && !sp.isSpectator()) {
            enter(level, pos, sp);
        }
    }

    static void enter(Level level, BlockPos pos, ServerPlayer player) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) return;
        String key = player.getUUID() + "@" + pos.asLong();
        long now = level.getGameTime();
        Long last = STEP_COOLDOWN.get(key);
        if (last != null && now - last < 20) return;
        STEP_COOLDOWN.put(key, now);
        if (STEP_COOLDOWN.size() > 4096) STEP_COOLDOWN.clear();
        Id id = ensureDevice(level, pos);
        if (id != null) engine.interactions().enter(player.getUUID(), id);
    }

    /** Mirrors engine device state into block state (lamps light up, switches show as on). */
    public static void showState(Level level, BlockPos pos, Device device, boolean active, boolean powered) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EngineBlock)) return;
        BlockState next = state.setValue(ACTIVE, active).setValue(POWERED, powered);
        if (next != state) level.setBlock(pos, next, Block.UPDATE_ALL);
    }
}

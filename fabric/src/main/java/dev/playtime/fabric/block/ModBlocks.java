package dev.playtime.fabric.block;

import dev.playtime.engine.graph.DeviceType;
import dev.playtime.fabric.PlaytimeMod;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Every engine block, its item, and the shared block entity type. */
public final class ModBlocks {
    /** Block name to device type. Step triggers also fire when walked on. */
    public static final Map<String, EngineBlock> DEVICES = new LinkedHashMap<>();
    public static final List<Block> ALL = new ArrayList<>();

    public static Block FACTORY_DOOR;
    public static Block FACTORY_LIGHT;
    public static TriggerVolumeBlock TRIGGER_VOLUME;
    public static BlockEntityType<EngineBlockEntity> ENGINE_BLOCK_ENTITY;

    private ModBlocks() {}

    public static void register() {
        device("player_setup_console", DeviceType.SETUP_CONSOLE, false);
        device("door_controller", DeviceType.DOOR_CONTROLLER, false);
        device("keycard_reader", DeviceType.KEYCARD_READER, false);
        device("hand_scanner", DeviceType.HAND_SCANNER, false);
        device("access_terminal", DeviceType.ACCESS_TERMINAL, false);
        device("security_terminal", DeviceType.SECURITY_TERMINAL, false);
        device("power_generator", DeviceType.GENERATOR, false);
        device("fuse_box", DeviceType.FUSE_BOX, false);
        device("electrical_node", DeviceType.POWER_NODE, false);
        device("power_switch", DeviceType.POWER_SWITCH, false);
        device("power_receiver", DeviceType.POWER_RECEIVER, false);
        device("charging_station", DeviceType.CHARGING_STATION, false);
        device("battery", DeviceType.BATTERY_DEVICE, false);
        device("circuit_endpoint", DeviceType.CIRCUIT_ENDPOINT, false);
        device("pulse_receiver", DeviceType.PULSE_RECEIVER, false);
        device("timed_switch", DeviceType.TIMED_SWITCH, false);
        device("factory_button", DeviceType.BUTTON, false);
        device("factory_lever", DeviceType.LEVER, false);
        device("machine", DeviceType.MACHINE, false);
        device("part_socket", DeviceType.PART_SOCKET, false);
        device("alarm", DeviceType.ALARM, false);
        device("launch_pad", DeviceType.LAUNCH_PAD, true);
        device("attach_point", DeviceType.ATTACH_POINT, false);
        device("hand_pickup", DeviceType.HAND_PICKUP, false);
        device("puzzle_controller", DeviceType.PUZZLE_CONTROLLER, false);
        device("chase_trigger", DeviceType.CHASE_TRIGGER, true);
        device("checkpoint", DeviceType.CHECKPOINT, true);
        device("monster_spawn_marker", DeviceType.MONSTER_SPAWN, false);
        device("monster_route_marker", DeviceType.ROUTE_MARKER, false);
        device("story_event_trigger", DeviceType.STORY_TRIGGER, true);
        device("objective_trigger", DeviceType.OBJECTIVE_TRIGGER, true);
        device("chapter_transition", DeviceType.CHAPTER_TRANSITION, true);
        device("logic_gate", DeviceType.LOGIC_GATE, false);
        device("timer", DeviceType.TIMER, false);
        TRIGGER_VOLUME = register("trigger_volume", TriggerVolumeBlock::new,
                BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(-1.0F, 3600000.0F).noLootTable());
        DEVICES.put("trigger_volume", TRIGGER_VOLUME);

        FACTORY_DOOR = register("factory_door", Block::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable());
        FACTORY_LIGHT = register("factory_light", FactoryLightBlock::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.GLASS)
                .lightLevel(state -> state.getValue(FactoryLightBlock.LIT) ? 15 : 0));

        ENGINE_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PlaytimeMod.id("engine_device"),
                FabricBlockEntityTypeBuilder.create(EngineBlockEntity::new, DEVICES.values().toArray(new Block[0])).build());
    }

    private static void device(String name, DeviceType type, boolean step) {
        EngineBlock block = register(name, p -> new EngineBlock(type, step, p), BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable()
                .lightLevel(state -> state.getValue(EngineBlock.POWERED) ? 4 : 0));
        DEVICES.put(name, block);
    }

    private static <B extends Block> B register(String name, Function<BlockBehaviour.Properties, B> factory,
                                                BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PlaytimeMod.id(name));
        B block = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, PlaytimeMod.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        ALL.add(block);
        return block;
    }

    /** Block name for a device type (used when building the sample). */
    public static EngineBlock forType(DeviceType type) {
        for (EngineBlock b : DEVICES.values()) if (b.deviceType() == type) return b;
        return null;
    }
}

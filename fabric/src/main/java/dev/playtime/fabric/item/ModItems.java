package dev.playtime.fabric.item;

import dev.playtime.fabric.PlaytimeMod;
import dev.playtime.fabric.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModItems {
    public static final List<Item> ITEMS = new ArrayList<>();
    public static Item CONNECTION_DEVICE;

    private ModItems() {}

    public static void register() {
        CONNECTION_DEVICE = item("connection_device", ConnectionDeviceItem::new, new Item.Properties().stacksTo(1));
        for (String part : new String[]{"power_core", "fuse", "gear", "drive_belt"}) {
            item(part, Item::new, new Item.Properties().stacksTo(16));
        }
        for (String card : new String[]{"blue_access_card", "red_access_card", "high_security_card",
                "maintenance_key", "laboratory_key"}) {
            item(card, Item::new, new Item.Properties().stacksTo(1));
        }

        ResourceKey<CreativeModeTab> tools = ResourceKey.create(Registries.CREATIVE_MODE_TAB, PlaytimeMod.id("tools"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tools, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.playtime.tools"))
                .icon(() -> new ItemStack(ModBlocks.DEVICES.get("player_setup_console")))
                .displayItems((params, output) -> {
                    output.accept(CONNECTION_DEVICE);
                    for (Block b : ModBlocks.ALL) output.accept(b);
                })
                .build());
        ResourceKey<CreativeModeTab> items = ResourceKey.create(Registries.CREATIVE_MODE_TAB, PlaytimeMod.id("items"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, items, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.playtime.items"))
                .icon(() -> new ItemStack(ITEMS.get(1)))
                .displayItems((params, output) -> {
                    for (Item i : ITEMS) if (i != CONNECTION_DEVICE) output.accept(i);
                })
                .build());
    }

    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PlaytimeMod.id(name));
        Item item = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ITEMS.add(item);
        return item;
    }
}

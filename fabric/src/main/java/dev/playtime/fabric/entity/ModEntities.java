package dev.playtime.fabric.entity;

import dev.playtime.fabric.PlaytimeMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static EntityType<PlaytimeMonster> MONSTER;

    private ModEntities() {}

    public static void register() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, PlaytimeMod.id("monster"));
        MONSTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.of(PlaytimeMonster::new, MobCategory.MONSTER)
                        .sized(0.8F, 2.0F).clientTrackingRange(10).noSave().build(key));
        FabricDefaultAttributeRegistry.register(MONSTER, PlaytimeMonster.attributes());
    }
}

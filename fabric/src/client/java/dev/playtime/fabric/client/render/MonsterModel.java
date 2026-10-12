package dev.playtime.fabric.client.render;

import dev.playtime.fabric.entity.PlaytimeMonster;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.HashMap;
import java.util.Map;

/**
 * Picks the model, texture and animations for a monster from its engine id:
 * {@code playtime:the_playwright} uses {@code geckolib/models/monsters/the_playwright.geo.json},
 * {@code textures/entity/monsters/the_playwright.png} and the matching animation file.
 * Characters without a model yet fall back to The Playwright so they are still visible.
 */
public class MonsterModel extends GeoModel<PlaytimeMonster> {
    public static final DataTicket<String> MONSTER_ID = DataTicket.create("playtime_monster", String.class);
    private static final String FALLBACK = "playtime:the_playwright";
    private static final Map<String, Boolean> HAS_MODEL = new HashMap<>();

    private static String resolve(String monsterId) {
        if (monsterId == null || monsterId.isEmpty()) return FALLBACK;
        return HAS_MODEL.computeIfAbsent(monsterId, id -> {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) return false;
            Identifier file = Identifier.fromNamespaceAndPath(parsed.getNamespace(), "geckolib/models/monsters/" + parsed.getPath() + ".geo.json");
            return Minecraft.getInstance().getResourceManager().getResource(file).isPresent();
        }) ? monsterId : FALLBACK;
    }

    private static Identifier withFolder(String id, String prefix, String suffix) {
        Identifier parsed = Identifier.parse(id);
        return Identifier.fromNamespaceAndPath(parsed.getNamespace(), prefix + parsed.getPath() + suffix);
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return withFolder(resolve(renderState.getGeckolibData(MONSTER_ID)), "monsters/", "");
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return withFolder(resolve(renderState.getGeckolibData(MONSTER_ID)), "textures/entity/monsters/", ".png");
    }

    @Override
    public Identifier getAnimationResource(PlaytimeMonster animatable) {
        return withFolder(resolve(animatable.monsterId()), "monsters/", "");
    }
}

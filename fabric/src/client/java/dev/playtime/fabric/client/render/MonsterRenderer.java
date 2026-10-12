package dev.playtime.fabric.client.render;

import dev.playtime.fabric.entity.PlaytimeMonster;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** GeckoLib renderer shared by every engine monster; the model is chosen per monster id. */
public class MonsterRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<PlaytimeMonster, R> {
    public MonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new MonsterModel());
    }

    @Override
    public void addRenderData(PlaytimeMonster animatable, @Nullable Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(MonsterModel.MONSTER_ID, animatable.monsterId());
    }
}

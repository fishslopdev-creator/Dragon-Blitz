package dev.playtime.fabric.mixin;

import dev.playtime.fabric.server.PlayerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the world's death rules before items drop; keeps the inventory when the map says so. */
@Mixin(Player.class)
public abstract class PlayerDropMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void playtime$keepInventory(ServerLevel level, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && PlayerEvents.onDeath(player)) {
            ci.cancel();
        }
    }
}

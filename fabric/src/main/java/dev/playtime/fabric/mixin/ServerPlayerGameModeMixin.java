package dev.playtime.fabric.mixin;

import dev.playtime.fabric.server.PlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reports game-mode changes (e.g. /gamemode survival) so the main menu can open. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerGameModeMixin {
    @Unique
    private GameType playtime$previousMode;

    @Inject(method = "setGameMode", at = @At("HEAD"))
    private void playtime$before(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        playtime$previousMode = ((ServerPlayer) (Object) this).gameMode.getGameModeForPlayer();
    }

    @Inject(method = "setGameMode", at = @At("RETURN"))
    private void playtime$after(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && playtime$previousMode != null) {
            PlayerEvents.onGameModeChanged((ServerPlayer) (Object) this, playtime$previousMode, gameType);
        }
    }
}

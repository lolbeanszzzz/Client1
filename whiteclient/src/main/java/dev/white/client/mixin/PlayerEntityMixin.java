package dev.white.client.mixin;

import dev.white.client.module.impl.Reach;
import dev.white.client.module.impl.SafeWalk;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getBlockInteractionRange", at = @At("RETURN"), cancellable = true, require = 0)
    private void white$blockReach(CallbackInfoReturnable<Double> cir) {
        double r = Reach.block();
        if (r > 0 && (Object) this == MinecraftClient.getInstance().player) cir.setReturnValue(Math.max(r, cir.getReturnValue()));
    }

    @Inject(method = "getEntityInteractionRange", at = @At("RETURN"), cancellable = true, require = 0)
    private void white$entityReach(CallbackInfoReturnable<Double> cir) {
        double r = Reach.entity();
        if (r > 0 && (Object) this == MinecraftClient.getInstance().player) cir.setReturnValue(Math.max(r, cir.getReturnValue()));
    }

    // edge-clip (like sneaking) while Safe Walk sees a harmful drop ahead
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true, require = 0)
    private void white$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if (SafeWalk.clip() && (Object) this == MinecraftClient.getInstance().player) cir.setReturnValue(true);
    }
}

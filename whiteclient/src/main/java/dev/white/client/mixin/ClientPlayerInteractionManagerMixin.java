package dev.white.client.mixin;

import dev.white.client.module.impl.AutoTunnel;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    // vanilla cancels block breaking every tick the attack key is not held; Auto Tunnel keeps its progress
    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true, require = 0)
    private void white$keepBreaking(CallbackInfo ci) {
        if (AutoTunnel.breaking()) ci.cancel();
    }
}

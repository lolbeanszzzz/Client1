package dev.white.client.mixin;

import dev.white.client.module.impl.Freeze;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * sendPacket is declared on ClientCommonNetworkHandler (the parent of ClientPlayNetworkHandler since 1.20.2),
 * so it cannot be targeted from a mixin on ClientPlayNetworkHandler: Mixin only looks in the exact target class.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {
    /** Freeze: movement packets never leave the client, so the server keeps you where you froze. */
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void white$freeze(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof PlayerMoveC2SPacket && Freeze.holding()) ci.cancel();
    }
}

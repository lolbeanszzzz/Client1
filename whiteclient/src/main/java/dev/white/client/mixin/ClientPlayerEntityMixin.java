package dev.white.client.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Movement packets always use the real camera yaw/pitch so WASD and Grim's
 * movement simulation stay vanilla. Silent aim angles are sent separately as
 * pure Look packets via {@link dev.white.client.util.Rotations#sendLookPacket()}.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    // After a forced silent look packet the server still holds the silent angles. Vanilla only puts the rotation into a
    // movement packet when it changed, so nudge the real yaw by a hair (0.03 deg, invisible) to make this tick's
    // movement packet carry the real camera angles again.
    @Inject(method = "sendMovementPackets", at = @At("HEAD"), require = 0)
    private void white$restoreLook(CallbackInfo ci) {
        if (dev.white.client.util.Rotations.consumeRestore()) {
            net.minecraft.client.network.ClientPlayerEntity self = (net.minecraft.client.network.ClientPlayerEntity) (Object) this;
            self.setYaw(self.getYaw() + 0.03f);
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"), require = 0)
    private void white$markMoved(CallbackInfo ci) {
        dev.white.client.util.TickPhase.markMoved();
    }
}

package dev.white.client.mixin;

import dev.white.client.module.impl.Freecam;
import dev.white.client.module.impl.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    // mouse movement steers the freecam instead of the real player
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void white$look(double dx, double dy, CallbackInfo ci) {
        if (Freecam.active() && (Object) this == MinecraftClient.getInstance().player) {
            Freecam.get().look(dx, dy);
            ci.cancel();
        } else if (Freelook.active() && (Object) this == MinecraftClient.getInstance().player) {
            Freelook.get().look(dx, dy);   // camera only; the player keeps facing the same way
            ci.cancel();
        }
    }

    // silent rotation: your look vector (crosshair picking, ray casts) follows the rotation the server sees
    @Inject(method = "getRotationVec", at = @At("HEAD"), cancellable = true, require = 0)
    private void white$silentView(float tickDelta, CallbackInfoReturnable<net.minecraft.util.math.Vec3d> cir) {
        if (dev.white.client.util.Rotations.active() && (Object) this == MinecraftClient.getInstance().player)
            cir.setReturnValue(dev.white.client.util.Rotations.viewVector());
    }

    // Hitbox Expand: a bigger aiming box for other entities (crosshair / attack picking only)
    @Inject(method = "getTargetingMargin", at = @At("RETURN"), cancellable = true, require = 0)
    private void white$margin(CallbackInfoReturnable<Float> cir) {
        float m = dev.white.client.module.impl.HitboxExpand.margin((Entity) (Object) this);
        if (m > 0f) cir.setReturnValue(cir.getReturnValueF() + m);
    }
}

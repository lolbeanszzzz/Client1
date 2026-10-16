package dev.white.client.mixin;

import dev.white.client.module.impl.Freecam;
import dev.white.client.module.impl.Freelook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void white$update(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                              float tickProgress, CallbackInfo ci) {
        if (Freecam.active()) {
            Freecam f = Freecam.get();
            setRotation(f.yaw, f.pitch);
            setPos(f.lx(tickProgress), f.ly(tickProgress), f.lz(tickProgress));
        } else if (Freelook.active()) {
            setRotation(Freelook.get().yaw, Freelook.get().pitch);
        }
    }

    // makes the real player model render while the camera is detached
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void white$thirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.active()) cir.setReturnValue(true);
    }
}

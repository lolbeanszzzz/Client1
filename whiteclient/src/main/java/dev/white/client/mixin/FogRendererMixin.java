package dev.white.client.mixin;

import dev.white.client.module.impl.SkyModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the fog / horizon colour match the Sky module so the world fades into the white (or gray) sky.
 * getFogColor is the one fog method with a single overload (applyFog has two with different return types).
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void white$fog(Camera camera, float tickProgress, ClientWorld world, int viewDistance, float skyDarkness,
                           CallbackInfoReturnable<Vector4f> cir) {
        if (!SkyModule.active() || !SkyModule.get().fogOn()) return;
        int rgb = SkyModule.get().skyRgb();
        cir.setReturnValue(new Vector4f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f));
    }
}

package dev.white.client.mixin;

import dev.white.client.module.impl.SkyModule;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** White clouds for the Sky module: renderClouds(int color, ...) gets the white colour (its alpha is kept, e.g. for rain). */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true)
    private int white$clouds(int color) {
        if (!SkyModule.active() || !SkyModule.get().cloudsOn()) return color;
        return (color & 0xFF000000) | 0xFFFFFF;
    }
}

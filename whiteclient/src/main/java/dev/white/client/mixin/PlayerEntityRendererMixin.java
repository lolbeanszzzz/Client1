package dev.white.client.mixin;

import dev.white.client.cape.CapeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void kirx$useLocalCape(PlayerLikeEntity player, PlayerEntityRenderState state,
                                   float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (CapeManager.hasCape() && client.player != null
                && player.getUuid().equals(client.player.getUuid())) {
            state.skinTextures = CapeManager.withLocalCape(state.skinTextures);
            state.capeVisible = true;
        }
    }
}

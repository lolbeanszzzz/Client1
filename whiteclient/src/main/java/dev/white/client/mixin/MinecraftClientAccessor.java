package dev.white.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Fast Crystal clear the right-click repeat delay. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown")
    void white$setItemUseCooldown(int ticks);

    @Accessor("itemUseCooldown")
    int white$getItemUseCooldown();
}

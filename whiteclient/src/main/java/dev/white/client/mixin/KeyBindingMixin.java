package dev.white.client.mixin;

import dev.white.client.util.ForcedKeys;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void white$forced(CallbackInfoReturnable<Boolean> cir) {
        if (ForcedKeys.isForced((KeyBinding) (Object) this)) cir.setReturnValue(true);
    }
}

package dev.white.client.mixin;

import dev.white.client.module.impl.UsernameHider;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Username Hider hook: every string the text renderer draws or measures goes through this visitFormatted overload
 * (the shorter overloads call it), so swapping the string here covers chat, tab list, name tags, scoreboard and menus,
 * and keeps the measured width equal to the drawn width.
 */
@Mixin(TextVisitFactory.class)
public abstract class TextVisitFactoryMixin {
    @ModifyVariable(
            method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
            at = @At("HEAD"), argsOnly = true)
    private static String white$hideName(String text) {
        return UsernameHider.apply(text);
    }
}

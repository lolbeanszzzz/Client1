package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class Fullbright extends Module {
    private boolean applied;

    public Fullbright() { super("Fullbright", Category.RENDER); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur == null || cur.getDuration() < 400) {
            // client-side only; long duration avoids the fade-out flicker
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, true, false, false));
            applied = true;
        }
    }

    @Override
    protected void onDisable() {
        if (applied && mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        applied = false;
    }
}

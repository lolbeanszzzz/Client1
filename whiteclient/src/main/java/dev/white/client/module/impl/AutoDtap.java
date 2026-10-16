package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Crystal double tap: every hit on an end crystal is sent several times in the same tick (Taps), so the crystal
 * pops even if the first hit is dropped or lags. Works for your own clicks and, with Auto break, hits the crystal
 * your crosshair is on by itself.
 */
public class AutoDtap extends Module {
    private final Setting.Num taps = add(new Setting.Num("Taps", 2, 2, 4, 1));
    private final Setting.Bool autoBreak = add(new Setting.Bool("Auto break", true));
    private final Setting.Num delay = add(new Setting.Num("Auto delay (ticks)", 0, 0, 10, 1));
    private boolean busy;                  // our own extra hits must not trigger more extra hits
    private int wait;

    public AutoDtap() {
        super("Auto Dtap", Category.COMBAT);
        // runs just before an attack is sent: the extra taps go out first, then the real hit follows
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (isEnabled() && !busy && world.isClient() && player == mc.player && entity instanceof EndCrystalEntity)
                extraTaps(entity);
            return ActionResult.PASS;
        });
    }

    @Override protected void onEnable() { wait = 0; }

    private void extraTaps(net.minecraft.entity.Entity crystal) {
        if (mc.interactionManager == null) return;
        busy = true;
        try {
            for (int i = 1; i < taps.intValue(); i++) AttackPackets.hit(crystal);      // every tap is a swing + attack
        } finally {
            busy = false;
        }
    }

    @Override
    public void onTick() {
        if (!autoBreak.value) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || Freecam.active() || mc.player.isSpectator()) { wait = 0; return; }
        if (!(mc.crosshairTarget instanceof EntityHitResult hit) || hit.getType() != HitResult.Type.ENTITY
                || !(hit.getEntity() instanceof EndCrystalEntity crystal) || !crystal.isAlive()) { wait = 0; return; }
        if (++wait <= delay.intValue()) return;
        wait = 0;
        AttackPackets.hit(crystal);                                   // the callback above adds the extra taps
    }
}

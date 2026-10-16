package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/** Attacks the nearest valid target within range whenever the weapon is charged. */
public class KillAura extends Module {
    private final Setting.Num range = add(new Setting.Num("Range", 3.5, 1.0, 6.0, 0.1));
    private final Setting.Bool players = add(new Setting.Bool("Players", true));
    private final Setting.Bool mobs = add(new Setting.Bool("Other mobs", false));
    private final Setting.Num cooldown = add(new Setting.Num("Min charge", 0.95, 0.1, 1.0, 0.05));

    public KillAura() { super("Kill Aura", Category.COMBAT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || Freecam.active() || mc.player.isUsingItem() || mc.player.isSpectator()) return;

        Entity best = null;
        double bestD = range.value;
        for (Entity e : mc.world.getEntities()) {
            if (!valid(e)) continue;
            double d = mc.player.distanceTo(e);
            if (d <= bestD) { bestD = d; best = e; }
        }
        if (best == null) return;

        AutoWeapon.onTarget(best);                              // Auto Weapon: right weapon for this target, the hit follows a tick later

        // charge of the item you hold; an axe hit on a shield / a mace smash does not wait for it (Auto Weapon decides)
        if (!AutoWeapon.bypass(best) && AutoWeapon.progress() < cooldown.value) return;
        if (!AutoWeapon.readyToAttack()) return;                // the slot was changed this tick: hit next tick

        AttackPackets.hit(best);                                // swing + attack; sent before the next movement packet
    }

    private boolean valid(Entity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity p) return players.value && !p.isSpectator() && !dev.white.client.util.Friends.shield(p);
        return e instanceof LivingEntity && mobs.value;
    }
}

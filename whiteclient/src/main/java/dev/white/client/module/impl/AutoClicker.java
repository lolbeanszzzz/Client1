package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.Friends;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Hold the left mouse button while your crosshair is on a target and it attacks for you at a steady rate
 * (clicks per second). Clicks are spread evenly over the ticks; at most one per tick. Block breaking is left to
 * the game: it only acts while the crosshair is on an entity.
 */
public class AutoClicker extends Module {
    private final Setting.Num cps = add(new Setting.Num("CPS", 10, 1, 20, 1));
    private final Setting.Num minCharge = add(new Setting.Num("Min charge", 0.0, 0.0, 1.0, 0.05));
    private final Setting.Bool players = add(new Setting.Bool("Players", true));
    private final Setting.Bool mobs = add(new Setting.Bool("Other mobs", true));
    private final Setting.Bool pauseUsing = add(new Setting.Bool("Pause while using item", true));

    private double acc;

    public AutoClicker() { super("Auto Clicker", Category.COMBAT); }

    @Override protected void onEnable() { acc = 0; }

    private boolean mouseDown() {
        return mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || Freecam.active() || mc.player.isSpectator()) { acc = 0; return; }
        if (pauseUsing.value && mc.player.isUsingItem()) { acc = 0; return; }
        if (!mouseDown()) { acc = 0; return; }
        if (!(mc.crosshairTarget instanceof EntityHitResult hit) || hit.getType() != HitResult.Type.ENTITY) {
            acc = Math.min(acc, 1.0);   // keep the rhythm, but don't bank clicks while off target
            return;
        }
        Entity target = hit.getEntity();
        if (!valid(target)) return;

        AutoWeapon.onTarget(target);                          // Auto Weapon: right weapon for this target, the hit follows a tick later

        acc += cps.value / 20.0;
        if (acc < 1.0) return;
        if (minCharge.value > 0.0 && !AutoWeapon.bypass(target) && AutoWeapon.progress() < minCharge.value) return;   // keep the click, wait for charge
        if (!AutoWeapon.readyToAttack()) return;              // the slot was changed this tick: keep the click for next tick
        acc -= 1.0;

        AttackPackets.hit(target);
    }

    private boolean valid(Entity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity p) return players.value && !p.isSpectator() && !Friends.shield(p);
        return e instanceof LivingEntity && mobs.value;
    }
}

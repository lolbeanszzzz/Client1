package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Removes the walking slowdown while eating / drinking / blocking. Done without touching game code: while
 * you use an item the game scales your movement input by 0.2, so just before each tick this adds back the
 * missing speed so you end up moving at normal walking speed.
 */
public class NoSlow extends Module {
    public NoSlow() { super("No Slow", Category.MOVEMENT); }

    @Override
    public void onPreTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || Freecam.active()) return;
        if (!p.isUsingItem() || p.hasVehicle() || !p.isOnGround() || p.isSneaking() || p.isTouchingWater() || p.isClimbing()) return;

        GameOptions o = mc.options;
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        double yaw = Math.toRadians(p.getYaw());
        double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) return;
        dx /= len; dz /= len;

        // normal ground walking covers ~2.158 x movementSpeed per tick; the slowed input still supplies 0.2 x of it
        double ms = p.getMovementSpeed();
        double v = ms * (2.158 - 0.2);
        p.setVelocity(dx * v, p.getVelocity().y, dz * v);
    }
}

package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.client.option.GameOptions;

/** Free flight: move with the normal movement keys, Jump = up, Sneak = down. */
public class Flight extends Module {
    private final Setting.Num speed = add(new Setting.Num("Speed", 0.5, 0.1, 3.0, 0.1));

    public Flight() { super("Flight", Category.MOVEMENT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null || Freecam.active()) return;
        GameOptions o = mc.options;

        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        double u = (o.jumpKey.isPressed() ? 1 : 0) - (o.sneakKey.isPressed() ? 1 : 0);

        double yaw = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double len = Math.hypot(dx, dz);
        if (len > 1e-6) { dx /= len; dz /= len; }

        mc.player.setVelocity(dx * speed.value, u * speed.value, dz * speed.value);
    }
}

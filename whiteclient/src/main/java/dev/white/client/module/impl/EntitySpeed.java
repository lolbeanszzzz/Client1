package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Sets the horizontal speed of the entity you are riding (boat, horse, pig, minecart...) while you hold a movement key. */
public class EntitySpeed extends Module {
    private final Setting.Num speed = add(new Setting.Num("Speed", 1.0, 0.1, 5.0, 0.1));

    public EntitySpeed() { super("Entity Speed", Category.MOVEMENT); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null || Freecam.active()) return;
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) return;
        GameOptions o = mc.options;

        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        if (f == 0 && s == 0) return;

        double yaw = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double len = Math.hypot(dx, dz);
        dx /= len; dz /= len;

        Vec3d v = vehicle.getVelocity();
        vehicle.setVelocity(dx * speed.value, v.y, dz * speed.value);
    }
}

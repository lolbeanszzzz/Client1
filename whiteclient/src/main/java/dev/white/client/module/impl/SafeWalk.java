package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Stops you walking off an edge when the drop would hurt. Where the ground below is within "Max drop"
 * blocks (or there is water) you walk normally; where it is further, you are edge-clipped exactly as if
 * you were sneaking (see PlayerEntityMixin). The default of 3 matches vanilla: a 4+ block fall damages you.
 */
public class SafeWalk extends Module {
    private static SafeWalk instance;
    private final Setting.Num maxDrop = add(new Setting.Num("Max drop", 3, 1, 10, 1));
    private boolean danger;

    public SafeWalk() { super("Safe Walk", Category.MOVEMENT); instance = this; }

    /** True while the player should be edge-clipped this tick. */
    public static boolean clip() { return instance != null && instance.isEnabled() && instance.danger; }

    @Override protected void onDisable() { danger = false; }

    @Override
    public void onPreTick() {
        danger = false;
        if (mc.player == null || mc.world == null || mc.currentScreen != null || Freecam.active()) return;
        if (!mc.player.isOnGround() || mc.player.getAbilities().flying || mc.player.isTouchingWater()) return;

        // directions we are about to move in: held keys and current momentum
        GameOptions o = mc.options;
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        double yaw = Math.toRadians(mc.player.getYaw());
        double ix = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double iz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        Vec3d vel = mc.player.getVelocity();

        if (check(ix, iz) || check(vel.x, vel.z)) danger = true;
    }

    /** Samples the ground just ahead (and slightly to each side) in direction (dx, dz). */
    private boolean check(double dx, double dz) {
        double len = Math.hypot(dx, dz);
        if (len < 0.02) return false;
        dx /= len; dz /= len;
        double px = mc.player.getX(), pz = mc.player.getZ();
        int feetY = MathHelper.floor(mc.player.getY() + 0.001);
        for (double dist : new double[]{0.35, 0.8}) {
            for (double side : new double[]{-0.3, 0.0, 0.3}) {
                double x = px + dx * dist - dz * side;
                double z = pz + dz * dist + dx * side;
                if (drops(x, z, feetY)) return true;
            }
        }
        return false;
    }

    /** True if there is nothing solid (and no water) within maxDrop blocks below the feet at (x, z). */
    private boolean drops(double x, double z, int feetY) {
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
        for (int j = 0; j <= maxDrop.intValue(); j++) {
            BlockPos pos = new BlockPos(bx, feetY - 1 - j, bz);
            var st = mc.world.getBlockState(pos);
            if (!st.getCollisionShape(mc.world, pos).isEmpty() || !st.getFluidState().isEmpty()) return false;
        }
        return true;
    }
}

package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;

/** Bunny hop: jumps automatically every time you land while moving. Optional air speed (0 = vanilla). */
public class Bhop extends Module {
    private final Setting.Num airSpeed = add(new Setting.Num("Air speed", 0.0, 0.0, 0.6, 0.02));
    private boolean pressedByUs;

    public Bhop() { super("Bhop", Category.MOVEMENT); }

    @Override protected void onDisable() { release(); }

    private void release() {
        if (pressedByUs) { pressedByUs = false; KeyBinding.updatePressedStates(); } // re-sync with the real key
    }

    private static double[] moveDir(GameOptions o, double yawDeg) {
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        double yaw = Math.toRadians(yawDeg);
        double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
        double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
        double len = Math.hypot(dx, dz);
        return len < 1e-6 ? null : new double[]{dx / len, dz / len};
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.currentScreen != null || Freecam.active()) { release(); return; }
        GameOptions o = mc.options;
        boolean moving = moveDir(o, mc.player.getYaw()) != null;
        boolean ok = moving && mc.player.isOnGround() && !mc.player.isSneaking()
                && !mc.player.isTouchingWater() && !mc.player.isInLava() && !mc.player.isClimbing();
        if (ok) { o.jumpKey.setPressed(true); pressedByUs = true; }
        else release();
    }

    @Override
    public void onTick() {
        if (airSpeed.value <= 0 || mc.player == null || mc.currentScreen != null || Freecam.active()) return;
        if (mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.isClimbing()) return;
        double[] d = moveDir(mc.options, mc.player.getYaw());
        if (d == null) return;
        var v = mc.player.getVelocity();
        mc.player.setVelocity(d[0] * airSpeed.value, v.y, d[1] * airSpeed.value);
    }
}

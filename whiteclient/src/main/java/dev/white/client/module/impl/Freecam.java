package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam. The camera is moved by Camera/Entity mixins; the real player is frozen by
 * releasing its movement keys every tick while we read the physical keys ourselves.
 */
public class Freecam extends Module {
    private static Freecam instance;

    public final Setting.Num speed = add(new Setting.Num("Speed", 0.8, 0.1, 4.0, 0.1));

    public double x, y, z, px, py, pz;
    public float yaw, pitch;
    private Object owner;

    public Freecam() {
        super("Freecam", Category.PLAYER);
        instance = this;
    }

    public static Freecam get() { return instance; }
    public static boolean active() { return instance != null && instance.isEnabled(); }

    @Override
    protected void onEnable() {
        if (mc.player == null) { setEnabled(false); return; }
        owner = mc.player;
        Vec3d e = mc.player.getEyePos();
        x = px = e.x; y = py = e.y; z = pz = e.z;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
    }

    @Override
    protected void onDisable() {
        owner = null;
        KeyBinding.updatePressedStates(); // re-sync keys that were still held
    }

    @Override
    public void onPreTick() {
        // leaving the world / changing dimension replaces the player object
        if (mc.player == null || mc.player != owner) { setEnabled(false); return; }

        px = x; py = y; pz = z;
        GameOptions o = mc.options;

        // keep the real player frozen
        for (KeyBinding k : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey,
                o.jumpKey, o.sneakKey, o.sprintKey, o.attackKey, o.useKey}) {
            k.setPressed(false);
        }
        if (mc.currentScreen != null) return;

        long w = mc.getWindow().getHandle();
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);

        double f = (down(w, o.forwardKey) ? 1 : 0) - (down(w, o.backKey) ? 1 : 0);
        double s = (down(w, o.rightKey) ? 1 : 0) - (down(w, o.leftKey) ? 1 : 0);
        double u = (down(w, o.jumpKey) ? 1 : 0) - (down(w, o.sneakKey) ? 1 : 0);

        double dx = fx * f + rx * s, dy = fy * f + u, dz = fz * f + rz * s;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-6) {
            double sp = speed.value * (down(w, o.sprintKey) ? 2.0 : 1.0);
            x += dx / len * sp; y += dy / len * sp; z += dz / len * sp;
        }
    }

    private static boolean down(long window, KeyBinding k) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(k);
        return key.getCategory() == InputUtil.Type.KEYSYM
                && GLFW.glfwGetKey(window, key.getCode()) == GLFW.GLFW_PRESS;
    }

    /** Called by EntityMixin instead of turning the real player. */
    public void look(double dx, double dy) {
        yaw += (float) (dx * 0.15);
        pitch = MathHelper.clamp(pitch + (float) (dy * 0.15), -90f, 90f);
    }

    public double lx(float t) { return px + (x - px) * t; }
    public double ly(float t) { return py + (y - py) * t; }
    public double lz(float t) { return pz + (z - pz) * t; }
}

package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import net.minecraft.util.math.MathHelper;

/** Look around freely with the mouse while your character keeps facing (and walking) the same way. */
public class Freelook extends Module {
    private static Freelook instance;
    public float yaw, pitch;
    private Object owner;

    public Freelook() { super("Freelook", Category.RENDER); instance = this; }

    public static Freelook get() { return instance; }
    /** Not while Freecam is on: Freecam steers the camera itself. */
    public static boolean active() { return instance != null && instance.isEnabled() && !Freecam.active(); }

    @Override
    protected void onEnable() {
        if (mc.player == null) { setEnabled(false); return; }
        owner = mc.player;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
    }

    @Override protected void onDisable() { owner = null; }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.player != owner) setEnabled(false);   // new world / dimension
    }

    /** Called by EntityMixin instead of turning the real player. */
    public void look(double dx, double dy) {
        yaw += (float) (dx * 0.15);
        pitch = MathHelper.clamp(pitch + (float) (dy * 0.15), -90f, 90f);
    }
}

package dev.white.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Smoothly turns your REAL view toward an entity, one step per call (call it once per tick). Each step moves a share of
 * the remaining angle (so it eases out as it arrives), at least 1 degree and at most maxStep degrees, per axis.
 */
public final class SmoothLook {
    private SmoothLook() {}

    public static void step(Entity target, double maxStep) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) return;
        Vec3d eye = mc.player.getEyePos();
        double dx = target.getX() - eye.x, dz = target.getZ() - eye.z;
        double dy = target.getY() + target.getHeight() * 0.7 - eye.y;
        double h = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = h > 0.35 ? (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0) : mc.player.getYaw();
        float wantPitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, Math.max(h, 1.0E-4))), -90.0, 90.0);
        mc.player.setYaw(mc.player.getYaw() + ease(MathHelper.wrapDegrees(wantYaw - mc.player.getYaw()), maxStep));
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + ease(wantPitch - mc.player.getPitch(), maxStep), -90f, 90f));
    }

    private static float ease(float diff, double maxStep) {
        float a = Math.abs(diff);
        if (a < 0.05f) return diff;
        float s = Math.min(a, (float) Math.min(maxStep, Math.max(a * 0.35f, 1.0f)));
        return Math.copySign(s, diff);
    }
}

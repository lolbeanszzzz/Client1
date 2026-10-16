package dev.white.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Constructor;

/**
 * Silent (server-side) rotation without contaminating movement packets.
 *
 * Grim (and vanilla physics simulation) flags PosRot packets whose look direction
 * disagrees with the position delta. So we NEVER put silent yaw/pitch into the
 * regular movement packet. Instead:
 *   - Movement packets always carry your real camera angles (WASD stays vanilla).
 *   - When a module needs the server to raycast a different direction, it calls
 *     {@link #sendLookPacket()} which injects a pure LookAndOnGround packet.
 *
 * Client-side look vector (ray casts / crosshair helpers) still follows the silent
 * angles via EntityMixin#getRotationVec while active.
 */
public final class Rotations {
    private static Object owner;
    private static boolean active;
    private static float yaw, pitch;
    private static float lastSentYaw = Float.NaN, lastSentPitch = Float.NaN;
    private static Constructor<?> lookCtor;

    private Rotations() {}

    public static boolean active() { return active; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
    public static boolean ownedBy(Object o) { return owner == o; }
    public static boolean free() { return owner == null; }

    /** Takes (or keeps) ownership and sets the silent angles. False when somebody else owns it. */
    public static boolean set(Object who, float newYaw, float newPitch) {
        if (owner != null && owner != who) return false;
        owner = who;
        active = true;
        yaw = newYaw;
        pitch = MathHelper.clamp(newPitch, -90f, 90f);
        return true;
    }

    /** Gives the rotation back (only if "who" owns it). */
    public static void release(Object who) {
        if (owner != who) return;
        owner = null;
        active = false;
        lastSentYaw = Float.NaN;
        lastSentPitch = Float.NaN;
    }

    public static Vec3d viewVector() { return Vec3d.fromPolar(pitch, yaw); }

    /**
     * Injects a PlayerMoveC2SPacket.LookAndOnGround with the current silent angles.
     * Call this AFTER the tick's movement packet and BEFORE an attack so the server
     * raycast uses these angles without tying them to a position update (Grim-safe).
     *
     * @return true if a packet was sent (or angles were already the last ones sent)
     */
    public static boolean sendLookPacket() { return sendLookPacket(false); }

    private static boolean restorePending;

    /** True once after a forced silent look packet: the next movement packet must carry the real camera angles again. */
    public static boolean consumeRestore() { boolean r = restorePending; restorePending = false; return r; }

    /**
     * force = send even when these angles were already sent earlier. Needed right before an attack: the movement
     * packets in between carry the real camera angles, so "last sent" no longer matches what the server holds.
     */
    public static boolean sendLookPacket(boolean force) {
        if (!active) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return false;

        // skip if we already told the server these exact angles this session
        if (!force && !Float.isNaN(lastSentYaw)
                && Math.abs(MathHelper.wrapDegrees(yaw - lastSentYaw)) < 0.01f
                && Math.abs(pitch - lastSentPitch) < 0.01f) {
            return true;
        }

        try {
            if (lookCtor == null) {
                for (Constructor<?> c : PlayerMoveC2SPacket.LookAndOnGround.class.getConstructors()) {
                    Class<?>[] t = c.getParameterTypes();
                    if (t.length >= 3 && t[0] == float.class && t[1] == float.class && t[2] == boolean.class) {
                        lookCtor = c;
                        break;
                    }
                }
            }
            if (lookCtor == null) return false;
            Object[] a = new Object[lookCtor.getParameterCount()];
            a[0] = yaw;
            a[1] = pitch;
            for (int i = 2; i < a.length; i++) a[i] = Boolean.FALSE;
            if (mc.player.isOnGround() && a.length > 2) a[2] = Boolean.TRUE;
            mc.getNetworkHandler().sendPacket((Packet<?>) lookCtor.newInstance(a));
            lastSentYaw = yaw;
            lastSentPitch = pitch;
            restorePending = true;
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}

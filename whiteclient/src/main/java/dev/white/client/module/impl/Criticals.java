package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;

import java.lang.reflect.Constructor;

/**
 * Makes your hits critical while you stand on the ground: just before the attack, two small "in the air"
 * position packets are sent so the server sees you falling. Many anti-cheats flag this.
 */
public class Criticals extends Module {
    private static Constructor<?> ctor;

    public Criticals() {
        super("Criticals", Category.COMBAT);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (isEnabled() && world.isClient() && player == mc.player && entity instanceof LivingEntity) crit();
            return ActionResult.PASS;
        });
    }

    private void crit() {
        var p = mc.player;
        if (p == null || mc.getNetworkHandler() == null) return;
        if (!p.isOnGround() || p.isTouchingWater() || p.isInLava() || p.hasVehicle() || p.isClimbing()) return;
        send(p.getX(), p.getY() + 0.0625, p.getZ());
        send(p.getX(), p.getY(), p.getZ());
    }

    /** Built by reflection so it works whichever way this version's packet constructor is shaped. */
    private void send(double x, double y, double z) {
        try {
            if (ctor == null) {
                for (Constructor<?> c : PlayerMoveC2SPacket.PositionAndOnGround.class.getConstructors()) {
                    Class<?>[] t = c.getParameterTypes();
                    if (t.length >= 4 && t[0] == double.class && t[1] == double.class && t[2] == double.class && t[3] == boolean.class) { ctor = c; break; }
                }
            }
            if (ctor == null) return;
            Object[] a = new Object[ctor.getParameterCount()];
            a[0] = x; a[1] = y; a[2] = z;
            for (int i = 3; i < a.length; i++) a[i] = Boolean.FALSE;      // onGround = false (and horizontalCollision = false)
            mc.getNetworkHandler().sendPacket((Packet<?>) ctor.newInstance(a));
        } catch (ReflectiveOperationException ignored) {}
    }
}

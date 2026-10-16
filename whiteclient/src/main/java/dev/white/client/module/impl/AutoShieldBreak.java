package dev.white.client.module.impl;

import dev.white.client.util.SwapLimit;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * When your crosshair is on a player who is blocking with a shield (and facing you), switches to an axe from
 * your hotbar and hits them so the shield is disabled, then switches back once they stop blocking.
 */
public class AutoShieldBreak extends Module {
    private final Setting.Bool autoAttack = add(new Setting.Bool("Auto attack", true));
    private final Setting.Bool switchBack = add(new Setting.Bool("Switch back", true));
    private final Setting.Num delay = add(new Setting.Num("Delay (ticks)", 0, 0, 10, 1));
    private int prevSlot = -1, wait;

    private static AutoShieldBreak instance;
    /** True while the module is switched on (Trigger Bot's shield check backs off then). */
    static boolean active() { return instance != null && instance.isEnabled(); }

    public AutoShieldBreak() { super("Auto Shield Break", Category.COMBAT); instance = this; }

    @Override protected void onEnable() { prevSlot = -1; wait = 0; }
    @Override protected void onDisable() { if (prevSlot >= 0 && switchBack.value && mc.player != null) SwapLimit.selectNow(prevSlot); prevSlot = -1; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || Freecam.active() || mc.player.isSpectator()) { restore(); return; }

        PlayerEntity target = null;
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY
                && hit.getEntity() instanceof PlayerEntity p && p != mc.player && p.isAlive() && !p.isSpectator()
                && p.isBlocking() && facingUs(p)) target = p;

        if (target == null) { wait = 0; restore(); return; }

        var inv = mc.player.getInventory();
        if (!isAxe(inv.getStack(inv.getSelectedSlot()))) {
            int axe = -1;
            for (int i = 0; i < 9; i++) if (isAxe(inv.getStack(i))) { axe = i; break; }
            if (axe < 0) return;                                    // no axe in the hotbar
            int cur = inv.getSelectedSlot();
            if (!SwapLimit.select(axe)) return;                     // one swap per tick: try again next tick
            if (prevSlot < 0) prevSlot = cur;
        }
        if (!autoAttack.value) return;
        if (++wait <= delay.intValue()) return;
        wait = 0;
        AttackPackets.hit(target);                                  // syncs the selected slot to the server first
    }

    /** A shield only blocks hits that come from in front of its holder. */
    private boolean facingUs(PlayerEntity p) {
        Vec3d to = mc.player.getEyePos().subtract(p.getEyePos()).normalize();
        return p.getRotationVec(1.0f).dotProduct(to) > 0.0;
    }

    private static boolean isAxe(ItemStack s) { return !s.isEmpty() && s.isIn(ItemTags.AXES); }

    private void restore() {
        if (prevSlot >= 0 && switchBack.value && mc.player != null && !SwapLimit.select(prevSlot)) return;   // retried next tick
        prevSlot = -1;
    }
}

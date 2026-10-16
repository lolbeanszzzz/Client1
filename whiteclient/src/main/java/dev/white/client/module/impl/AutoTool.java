package dev.white.client.module.impl;

import dev.white.client.util.SwapLimit;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Auto Tool (Player): while you hold the attack button on a block, the fastest tool for that block in your hotbar is
 * selected for you (pickaxe on stone, axe on logs, shovel on dirt, sword on cobwebs...). When you stop mining it can put
 * your previous slot back. It never touches your slot when you are hitting an entity, in a menu, in creative or eating.
 */
public class AutoTool extends Module {
    private final Setting.Bool switchBack = add(new Setting.Bool("Switch back", true));
    private final Setting.Num backDelay = add(new Setting.Num("Switch back delay (ticks)", 4, 0, 20, 1));
    private final Setting.Bool saveDurability = add(new Setting.Bool("Save durability", true));

    private int previous = -1, restoreIn;

    public AutoTool() { super("Auto Tool", Category.PLAYER); }

    @Override protected void onDisable() { if (previous >= 0 && switchBack.value && mc.player != null) SwapLimit.selectNow(previous); previous = -1; }

    @Override
    public void onPreTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { previous = -1; return; }
        boolean blocked = mc.currentScreen != null || p.isCreative() || p.isSpectator() || Freecam.active() || AutoEat.eating();
        BlockHitResult hit = null;
        if (!blocked && mc.options.attackKey.isPressed()
                && mc.crosshairTarget instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) hit = b;

        if (hit != null) {
            select(p, mc.world.getBlockState(hit.getBlockPos()), hit);
            restoreIn = backDelay.intValue();
        } else if (previous >= 0 && --restoreIn <= 0) {
            restore();
        }
    }

    private void select(PlayerEntity p, BlockState st, BlockHitResult hit) {
        if (st.isAir() || st.getHardness(mc.world, hit.getBlockPos()) < 0) return;        // nothing to mine / unbreakable
        PlayerInventory inv = p.getInventory();
        int cur = inv.getSelectedSlot(), best = cur;
        float bestSpeed = speed(inv.getStack(cur), st);
        for (int i = 0; i < 9; i++) {
            if (i == cur) continue;
            ItemStack s = inv.getStack(i);
            if (saveDurability.value && nearlyBroken(s)) continue;
            float sp = speed(s, st);
            if (sp > bestSpeed + 0.01f) { best = i; bestSpeed = sp; }
        }
        if (best != cur) {
            if (!SwapLimit.select(best)) return;                  // one swap per tick: try again next tick
            if (previous < 0) previous = cur;
        }
    }

    private static float speed(ItemStack s, BlockState st) { return s.isEmpty() ? 1f : s.getMiningSpeedMultiplier(st); }

    private static boolean nearlyBroken(ItemStack s) { return s.isDamageable() && s.getMaxDamage() - s.getDamage() <= 2; }

    private void restore() {
        if (previous >= 0 && switchBack.value && mc.player != null && !SwapLimit.select(previous)) return;   // retried next tick
        previous = -1;
    }
}

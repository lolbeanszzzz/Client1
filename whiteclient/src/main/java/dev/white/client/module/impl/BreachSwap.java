package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.BlockPlacer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.ActionResult;

/**
 * Breach swap: the moment you attack an entity while holding something else (a sword by default), the
 * hit is made with a Breach mace from your hotbar instead, then your original slot is selected again.
 * Works for your own clicks and for Kill Aura / Trigger Bot hits too.
 */
public class BreachSwap extends Module {
    private final Setting.Bool breachOnly = add(new Setting.Bool("Breach only", true));   // off = any mace
    private final Setting.Bool fromSword = add(new Setting.Bool("Only from sword", true));
    private final Setting.Num delay = add(new Setting.Num("Swap back", 2, 1, 10, 1));    // ticks

    private int previous = -1, restoreIn;

    public BreachSwap() {
        super("Breach Swap", Category.COMBAT);
        // runs just before an attack is sent, so the slot change is synced to the server first
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (isEnabled() && world.isClient() && player == mc.player) swapToMace();
            return ActionResult.PASS;
        });
    }

    @Override protected void onDisable() { if (previous >= 0 && mc.player != null) BlockPlacer.selectForce(previous); previous = -1; }

    private void swapToMace() {
        if (mc.player == null || previous >= 0) return;                // already swapped for this hit
        ItemStack held = mc.player.getMainHandStack();
        if (held.isOf(Items.MACE)) return;
        if (fromSword.value && !held.isIn(ItemTags.SWORDS)) return;

        int slot = BlockPlacer.findSlot(s -> s.isOf(Items.MACE) && hasBreach(s));
        if (slot < 0 && !breachOnly.value) slot = BlockPlacer.findSlot(s -> s.isOf(Items.MACE));
        if (slot < 0) return;

        int cur = BlockPlacer.selected();
        if (!BlockPlacer.select(slot)) return;                          // one swap per tick: this hit goes out with what is held
        previous = cur;
        restoreIn = delay.intValue();
    }

    private static boolean hasBreach(ItemStack s) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.matchesKey(Enchantments.BREACH)) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (previous >= 0 && --restoreIn <= 0) restoreNow();
    }

    private void restoreNow() {
        if (previous >= 0 && mc.player != null && !BlockPlacer.select(previous)) return;   // retried next tick
        previous = -1;
    }
}

package dev.white.client.module.impl;

import dev.white.client.util.SwapLimit;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Wears the best armor you are carrying: fills empty armor slots and upgrades worse pieces
 * (netherite > diamond > iron > chain/copper > gold/turtle > leather, then most durability left).
 * Never swaps out an Elytra.
 */
public class AutoArmor extends Module {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int[] HANDLER_SLOT = {5, 6, 7, 8};
    private static final String[] SUFFIX = {"_helmet", "_chestplate", "_leggings", "_boots"};

    private final Setting.Num delay = add(new Setting.Num("Delay (ticks)", 2, 1, 10, 1));
    private int wait;

    public AutoArmor() { super("Auto Armor", Category.COMBAT); }

    private static int score(ItemStack s, int part) {
        if (s.isEmpty()) return -1;
        String id = Registries.ITEM.getId(s.getItem()).getPath();
        if (!id.endsWith(SUFFIX[part])) return -1;
        int mat = id.contains("netherite") ? 6 : id.contains("diamond") ? 5 : id.contains("iron") ? 4
                : id.contains("chainmail") || id.contains("copper") ? 3 : id.contains("gold") || id.contains("turtle") ? 2 : 1;
        int left = s.isDamageable() ? Math.max(0, s.getMaxDamage() - s.getDamage()) : 0;
        return mat * 10000 + Math.min(9999, left);
    }

    @Override
    public void onTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || p.isSpectator()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!p.currentScreenHandler.getCursorStack().isEmpty()) return;
        if (++wait < delay.intValue()) return;

        for (int part = 0; part < 4; part++) {
            ItemStack cur = p.getEquippedStack(SLOTS[part]);
            if (part == 1 && cur.isOf(Items.ELYTRA)) continue;
            int best = -1, bestScore = score(cur, part);
            for (int i = 0; i < 36; i++) {
                int sc = score(p.getInventory().getStack(i), part);
                if (sc > bestScore) { best = i; bestScore = sc; }
            }
            if (best < 0) continue;

            if (!SwapLimit.take()) return;                         // one swap per tick: try again next tick
            int sync = p.playerScreenHandler.syncId;
            int from = best < 9 ? 36 + best : best;                // inventory index -> player screen handler slot
            if (cur.isEmpty()) {
                mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.QUICK_MOVE, p);   // shift-click equips
            } else {
                mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, p);
                mc.interactionManager.clickSlot(sync, HANDLER_SLOT[part], 0, SlotActionType.PICKUP, p);
                mc.interactionManager.clickSlot(sync, from, 0, SlotActionType.PICKUP, p);
            }
            wait = 0;
            return;
        }
    }
}

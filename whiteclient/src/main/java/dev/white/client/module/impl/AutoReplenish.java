package dev.white.client.module.impl;

import dev.white.client.util.SwapLimit;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/** When a hotbar stack runs out (last block placed, tool broke...), refills that slot from the same item in your inventory. */
public class AutoReplenish extends Module {
    private final Setting.Num delay = add(new Setting.Num("Delay (ticks)", 1, 1, 10, 1));
    private final Item[] last = new Item[9];
    private int wait;

    public AutoReplenish() { super("Auto Replenish", Category.PLAYER); }

    @Override protected void onEnable() { java.util.Arrays.fill(last, null); wait = 0; }

    @Override
    public void onTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;

        boolean screenOpen = mc.currentScreen != null;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (screenOpen || !st.isEmpty()) last[i] = st.isEmpty() ? null : st.getItem();   // only an emptied slot triggers a refill
        }
        if (screenOpen && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (screenOpen) return;                                    // moving items by hand: never fight it
        if (++wait < delay.intValue()) return;

        for (int i = 0; i < 9; i++) {
            if (last[i] == null || !p.getInventory().getStack(i).isEmpty()) continue;
            for (int inv = 9; inv < 36; inv++) {
                ItemStack c = p.getInventory().getStack(inv);
                if (c.isEmpty() || c.getItem() != last[i]) continue;
                if (!SwapLimit.take()) return;                     // one swap per tick: try again next tick
                // SWAP with button i trades that inventory slot with hotbar slot i
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, inv, i, SlotActionType.SWAP, p);
                last[i] = null;
                wait = 0;
                return;
            }
            last[i] = null;                                        // nothing left to refill with
        }
    }
}

package dev.white.client.module.impl;

import dev.white.client.util.SwapLimit;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One-shot inventory sort: switch it on and it groups your main inventory (not the hotbar or armor) by item,
 * biggest stacks first, one swap at a time, then switches itself off. Bind a key to it for a sort button.
 */
public class InvSorter extends Module {
    private final Setting.Num delay = add(new Setting.Num("Delay (ticks)", 1, 1, 5, 1));
    private final Deque<int[]> swaps = new ArrayDeque<>();
    private int wait;

    public InvSorter() { super("Inv Sorter", Category.PLAYER); }

    @Override
    protected void onEnable() {
        swaps.clear();
        wait = 0;
        if (mc.player == null) { setEnabled(false); return; }

        int[] keys = new int[27];
        for (int k = 0; k < 27; k++) {
            ItemStack s = mc.player.getInventory().getStack(9 + k);
            keys[k] = s.isEmpty() ? Integer.MAX_VALUE : Registries.ITEM.getRawId(s.getItem()) * 128 + (127 - Math.min(127, s.getCount()));
        }
        for (int i = 0; i < 27; i++) {                     // selection sort, recording each swap
            int m = i;
            for (int j = i + 1; j < 27; j++) if (keys[j] < keys[m]) m = j;
            if (m != i) {
                int t = keys[i]; keys[i] = keys[m]; keys[m] = t;
                swaps.add(new int[]{9 + i, 9 + m});         // player screen handler slots 9..35 = main inventory
            }
        }
        if (swaps.isEmpty()) setEnabled(false);
    }

    @Override protected void onDisable() { swaps.clear(); }

    @Override
    public void onTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!p.currentScreenHandler.getCursorStack().isEmpty()) return;   // something already on the cursor
        if (++wait < delay.intValue()) return;
        wait = 0;

        int[] sw = swaps.peek();
        if (sw == null) { setEnabled(false); return; }
        if (!SwapLimit.take()) return;                      // one swap per tick: try again next tick
        swaps.poll();
        int sync = p.playerScreenHandler.syncId;
        // pick up A, drop on B (swaps them), put what came back down on A
        mc.interactionManager.clickSlot(sync, sw[0], 0, SlotActionType.PICKUP, p);
        mc.interactionManager.clickSlot(sync, sw[1], 0, SlotActionType.PICKUP, p);
        mc.interactionManager.clickSlot(sync, sw[0], 0, SlotActionType.PICKUP, p);
        if (swaps.isEmpty()) setEnabled(false);
    }
}

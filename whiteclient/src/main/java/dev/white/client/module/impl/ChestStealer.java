package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.HashSet;
import java.util.Set;

/** While a chest / barrel-style container or shulker box is open, shift-clicks everything out into your inventory. */
public class ChestStealer extends Module {
    private final Setting.Num delay = add(new Setting.Num("Delay (ticks)", 1, 0, 10, 1));
    private final Setting.Bool autoClose = add(new Setting.Bool("Auto close", false));
    private final Set<Integer> tried = new HashSet<>();
    private int lastSync = -1, cooldown;

    public ChestStealer() { super("Chest Stealer", Category.PLAYER); }

    @Override protected void onDisable() { tried.clear(); lastSync = -1; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> hs)) { tried.clear(); lastSync = -1; return; }

        ScreenHandler h = hs.getScreenHandler();
        int size;
        if (h instanceof GenericContainerScreenHandler g) size = g.getRows() * 9;
        else if (h instanceof ShulkerBoxScreenHandler) size = 27;
        else return;

        if (h.syncId != lastSync) { tried.clear(); lastSync = h.syncId; cooldown = delay.intValue(); }
        if (cooldown > 0) { cooldown--; return; }

        for (int i = 0; i < size; i++) {
            if (tried.contains(i)) continue;
            tried.add(i);                                  // each slot is tried once, so a full inventory can't loop forever
            if (h.getSlot(i).getStack().isEmpty()) continue;
            mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            cooldown = delay.intValue();
            return;
        }
        if (autoClose.value) mc.player.closeHandledScreen();
    }
}

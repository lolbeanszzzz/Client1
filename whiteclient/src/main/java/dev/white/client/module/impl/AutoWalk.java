package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import net.minecraft.client.option.KeyBinding;

/** Holds the forward key for you. Pauses while a screen is open or in Freecam. */
public class AutoWalk extends Module {
    private boolean pressedByUs;

    public AutoWalk() { super("Auto Walk", Category.MOVEMENT); }

    @Override protected void onDisable() { release(); }

    private void release() {
        if (pressedByUs) { pressedByUs = false; KeyBinding.updatePressedStates(); } // re-sync with the real key
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.currentScreen != null || Freecam.active()) { release(); return; }
        mc.options.forwardKey.setPressed(true);
        pressedByUs = true;
    }
}

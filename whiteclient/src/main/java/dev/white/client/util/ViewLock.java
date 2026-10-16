package dev.white.client.util;

import net.minecraft.client.MinecraftClient;

/**
 * Keeps world overlays glued to the blocks they mark.
 *
 * The overlays are projected from the camera position and the BASE field of view. Vanilla then draws the real
 * world with extra effects the overlays know nothing about: view bobbing (the camera sway while walking) and the
 * FOV change while sprinting, flying, under a speed effect or drawing a bow. Those make boxes slide off their
 * block. While an overlay module is on, this switches both effects off (using the normal in-game options) and
 * puts your own values back as soon as no overlay needs them, when you leave the world, or when the game closes.
 */
public final class ViewLock {
    private static boolean held;
    private static boolean origBob;
    private static double origFovEffect;

    private ViewLock() {}

    public static void update(MinecraftClient mc, boolean want) {
        if (mc.options == null) return;
        if (want && !held) {
            origBob = mc.options.getBobView().getValue();
            origFovEffect = mc.options.getFovEffectScale().getValue();
            mc.options.getBobView().setValue(false);
            mc.options.getFovEffectScale().setValue(0.0);
            held = true;
        } else if (!want && held) {
            release(mc);
        }
    }

    public static void release(MinecraftClient mc) {
        if (!held || mc.options == null) return;
        mc.options.getBobView().setValue(origBob);
        mc.options.getFovEffectScale().setValue(origFovEffect);
        held = false;
    }
}

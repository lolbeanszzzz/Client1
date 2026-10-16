package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.ForcedKeys;
import net.minecraft.client.option.KeyBinding;

/**
 * Jump reset: the moment you are hit while standing on the ground, you jump for one tick. The upward
 * movement cuts the horizontal knockback you would take from a ground hit. It presses the jump key through
 * the same forced-key hook the other modules use, so it behaves like a real keypress.
 */
public class JumpReset extends Module {
    private final Setting.Num chance = add(new Setting.Num("Chance %", 100, 10, 100, 5));
    private boolean forcing;
    private int lastHurt;

    public JumpReset() { super("Jump Reset", Category.COMBAT); }

    @Override protected void onEnable() { lastHurt = 0; }
    @Override protected void onDisable() { release(); }

    @Override
    public void onPreTick() {
        release();                                                  // the jump lasts a single tick
        if (mc.player == null || mc.world == null) return;
        int hurt = mc.player.hurtTime;
        boolean fresh = hurt > lastHurt && hurt >= 8;               // damage was taken since the last tick
        lastHurt = hurt;
        if (!fresh) return;
        if (mc.currentScreen != null || Freecam.active() || mc.player.isSpectator()) return;
        if (!mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isInLava()
                || mc.player.hasVehicle() || mc.player.getAbilities().flying) return;
        if (Math.random() * 100 >= chance.value) return;
        ForcedKeys.force(mc.options.jumpKey);
        forcing = true;
    }

    private void release() {
        if (!forcing) return;
        forcing = false;
        ForcedKeys.release(mc.options.jumpKey);
        KeyBinding.updatePressedStates();
    }
}

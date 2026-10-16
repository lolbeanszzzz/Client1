package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Vape-style Sprint for Fabric 1.21.x.
 * Holds sprint when you are allowed to sprint.
 */
public class Sprint extends Module {
    private final Setting.Bool cancelInvis = add(new Setting.Bool("Cancel invis", false));
    private final Setting.Bool omni = add(new Setting.Bool("Omni (any direction)", false));

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        if (mc.options != null) mc.options.sprintKey.setPressed(false);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.options == null) return;
        if (mc.currentScreen != null) return;
        if (Freecam.active() || mc.player.isSpectator()) return;
        if (cancelInvis.value && mc.player.hasStatusEffect(StatusEffects.INVISIBILITY)) return;

        boolean forward = mc.options.forwardKey.isPressed();
        boolean strafe = mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()
                || mc.options.backKey.isPressed();
        boolean moving = omni.value ? (forward || strafe) : forward;

        boolean can = moving
                && !mc.player.isSneaking()
                && !mc.player.isGliding()
                && mc.player.getHungerManager().getFoodLevel() > 6
                && !mc.player.isTouchingWater();

        if (can) {
            KeyBinding sprint = mc.options.sprintKey;
            if (!sprint.isPressed()) sprint.setPressed(true);
            if (!mc.player.isSprinting()) mc.player.setSprinting(true);
        }
    }
}

package dev.white.client.module.impl;

import dev.white.client.util.SwapLimit;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.ForcedKeys;
import net.minecraft.block.BlockState;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Digs a tunnel straight ahead in the direction you face (1 or 2 blocks high) and optionally walks
 * forward as it opens. Works while Freecam is on: the tunnel follows the direction your real body faces
 * (Freecam only steers the camera), so you can look around freely while it digs. Stops at liquids next to the block it would break, and at unbreakable blocks.
 */
public class AutoTunnel extends Module {
    private static AutoTunnel instance;

    private final Setting.Num reach = add(new Setting.Num("Reach", 4.0, 1.0, 4.5, 0.5));
    private final Setting.Bool tall = add(new Setting.Bool("2 high", true));
    private final Setting.Bool walk = add(new Setting.Bool("Walk forward", true));
    private final Setting.Bool autoTool = add(new Setting.Bool("Auto tool", true));

    private BlockPos target;
    private boolean pressedByUs;
    private int prevSlot = -1;

    public AutoTunnel() { super("Auto Tunnel", Category.PLAYER); instance = this; }

    /** True while a block is being dug (read by ClientPlayerInteractionManagerMixin). */
    public static boolean breaking() { return instance != null && instance.isEnabled() && instance.target != null; }

    @Override protected void onDisable() { stopAll(); if (prevSlot >= 0 && mc.player != null) SwapLimit.selectNow(prevSlot); prevSlot = -1; }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator() || AutoEat.eating()) {
            stopAll();
            return;
        }

        Direction dir = mc.player.getHorizontalFacing();
        BlockPos base = mc.player.getBlockPos();
        int height = tall.value ? 2 : 1;
        int ahead = (int) Math.floor(reach.value);

        BlockPos next = null;
        boolean hazard = false;
        outer:
        for (int i = 1; i <= ahead; i++) {
            for (int h = 0; h < height; h++) {
                BlockPos pos = base.offset(dir, i).up(h);
                BlockState st = mc.world.getBlockState(pos);
                if (!st.getFluidState().isEmpty()) { hazard = true; break outer; }
                if (st.isAir() || st.getCollisionShape(mc.world, pos).isEmpty()) continue;   // already open
                if (st.getHardness(mc.world, pos) < 0 || liquidNextTo(pos)) { hazard = true; break outer; }
                next = pos;
                break outer;
            }
        }

        // walk once the square directly ahead is open
        boolean clear1 = true;
        for (int h = 0; h < height; h++) {
            BlockPos pos = base.offset(dir, 1).up(h);
            BlockState st = mc.world.getBlockState(pos);
            if (!st.getFluidState().isEmpty() || !st.getCollisionShape(mc.world, pos).isEmpty()) clear1 = false;
        }
        // forced, so Freecam releasing the real keys can't stop it
        if (walk.value && clear1 && !hazard) { ForcedKeys.force(mc.options.forwardKey); pressedByUs = true; }
        else releaseKey();

        if (next == null || mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(next)) > reach.value * reach.value) {
            cancelDig();
            return;
        }

        if (autoTool.value) selectTool(mc.world.getBlockState(next));
        target = next;
        mc.interactionManager.updateBlockBreakingProgress(next, dir.getOpposite());
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean liquidNextTo(BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!mc.world.getFluidState(pos.offset(d)).isEmpty()) return true;
        }
        return false;
    }

    private void selectTool(BlockState st) {
        int best = mc.player.getInventory().getSelectedSlot();
        float bestSpeed = mc.player.getInventory().getStack(best).getMiningSpeedMultiplier(st);
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            float sp = s.getMiningSpeedMultiplier(st);
            if (sp > bestSpeed) { best = i; bestSpeed = sp; }
        }
        if (best != mc.player.getInventory().getSelectedSlot()) {
            int cur = mc.player.getInventory().getSelectedSlot();
            if (!SwapLimit.select(best)) return;                    // one swap per tick: try again next tick
            if (prevSlot < 0) prevSlot = cur;
        }
    }

    private void cancelDig() {
        if (target == null) return;
        target = null;                                         // before cancelling, so the mixin lets it through
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
    }

    private void releaseKey() {
        if (pressedByUs) { pressedByUs = false; ForcedKeys.release(mc.options.forwardKey); KeyBinding.updatePressedStates(); }
    }

    private void stopAll() {
        releaseKey();
        cancelDig();
        if (prevSlot >= 0 && mc.player != null && !SwapLimit.select(prevSlot)) return;   // retried next tick
        prevSlot = -1;
    }
}

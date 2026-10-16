package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.BlockPlacer;
import dev.white.client.util.Pacing;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a fixed blocky structure, one block at a time, with solid blocks from the hotbar.
 * Look at the ground where it should go, then switch the module on: the structure is anchored at
 * the block you are looking at and faces the way you are facing. Stay within reach while it builds.
 * It switches itself off once everything is placed.
 */
public class DingDongBuilder extends Module {
    private final Setting.Num perTick = add(new Setting.Num("Blocks/tick", 2, 1, 4, 1));
    private final Setting.Num finish = add(new Setting.Num("Finish time (s)", 2.0, 0.0, 10.0, 0.5));   // seconds the whole action takes; 0 = old instant behaviour (Delay / Blocks per tick)
    private final Setting.Num delay = add(new Setting.Num("Delay", 1, 1, 10, 1));

    private final List<BlockPos> todo = new ArrayList<>();
    private int wait, restore = -1;

    public DingDongBuilder() { super("Ding Dong Builder", Category.PLAYER); }

    /** Blueprint in local coordinates: x = right, y = up, z = forward. Built bottom layer first. */
    private static List<int[]> blueprint() {
        List<int[]> b = new ArrayList<>();
        for (int x = -1; x <= 1; x++) b.add(new int[]{x, 0, 0});   // 3 flat in a straight line
        for (int y = 1; y <= 3; y++) b.add(new int[]{0, y, 0});    // 3 stacked on top of the middle block
        return b;
    }

    @Override
    protected void onEnable() {
        todo.clear();
        wait = 0;
        restore = -1;
        restoreDue = false;
        if (mc.player == null || !(mc.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            setEnabled(false);   // nothing to anchor to: look at a block first
            return;
        }
        BlockPos anchor = hit.getBlockPos().offset(hit.getSide());
        Direction f = mc.player.getHorizontalFacing();
        int fx = f.getOffsetX(), fz = f.getOffsetZ();
        int rx = -fz, rz = fx;                       // "right" of the facing direction
        for (int[] p : blueprint()) {
            todo.add(anchor.add(rx * p[0] + fx * p[2], p[1], rz * p[0] + fz * p[2]));
        }
    }

    @Override
    protected void onDisable() {
        forceRestore();
        todo.clear();
    }

    private boolean restoreDue;

    private void restoreSlot() {
        if (restore >= 0 && mc.player != null && !BlockPlacer.select(restore)) { restoreDue = true; return; }   // one swap per tick: retried next tick
        restore = -1;
        restoreDue = false;
    }

    private void forceRestore() {
        if (restore >= 0 && mc.player != null) BlockPlacer.selectForce(restore);
        restore = -1;
        restoreDue = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || Freecam.active()) return;
        if (restoreDue) restoreSlot();

        int slot = BlockPlacer.findSlot(BlockPlacer::isSolidBlock);
        if (slot < 0) return;                                           // need solid blocks in the hotbar

        Box me = mc.player.getBoundingBox();
        boolean remaining = false;
        List<BlockPos> need = new ArrayList<>();
        for (BlockPos p : todo) {
            if (!mc.world.getBlockState(p).isReplaceable()) continue;   // already built
            remaining = true;
            if (new Box(p).intersects(me)) continue;                    // standing in the way
            need.add(p);
        }
        if (!remaining) { setEnabled(false); return; }                  // finished
        if (need.isEmpty()) { wait = 0; return; }                       // only blocks you are standing in are left
        if (++wait < Pacing.gap(finish.value, todo.size(), delay.intValue())) return;   // paced: the whole build takes about "Finish time"

        int per = Pacing.perStep(finish.value, perTick.intValue());
        int placed = 0;
        for (BlockPos p : need) {
            int cur = BlockPlacer.selected();
            if (!BlockPlacer.select(slot)) return;                  // one swap per tick: try again next tick
            if (restore < 0) restore = cur;
            if (BlockPlacer.place(p) && ++placed >= per) break;
        }
        if (placed > 0) {
            wait = 0;
            if (Pacing.paced(finish.value) && restore >= 0) restoreSlot();
        }
    }
}

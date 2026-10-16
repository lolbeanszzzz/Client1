package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.BlockPlacer;
import dev.white.client.util.Pacing;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Boxes in the nearest player: a ring of blocks around their feet, a ring around their head and a
 * cap on top. Prefers obsidian, otherwise any solid hotbar block.
 */
public class AutoBox extends Module {
    private static final Direction[] SIDES = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

    private final Setting.Num range = add(new Setting.Num("Range", 5.0, 2.0, 6.0, 0.5));
    private final Setting.Num perTick = add(new Setting.Num("Blocks/tick", 1, 1, 4, 1));
    private final Setting.Num finish = add(new Setting.Num("Finish time (s)", 2.0, 0.0, 10.0, 0.5));   // seconds the whole action takes; 0 = old instant behaviour (Delay / Blocks per tick)
    private final Setting.Bool cap = add(new Setting.Bool("Top cap", true));
    private final Setting.Num delay = add(new Setting.Num("Delay", 1, 1, 10, 1));
    private int wait, restore = -1;

    public AutoBox() { super("Auto Box", Category.COMBAT); }

    @Override protected void onEnable() { wait = 0; restore = -1; }
    @Override protected void onDisable() { forceRestore(); }

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

        PlayerEntity t = BlockPlacer.nearestPlayer(range.value);
        if (t == null) { wait = 0; restoreSlot(); return; }
        int slot = BlockPlacer.findSlot(s -> s.isOf(Items.OBSIDIAN));
        if (slot < 0) slot = BlockPlacer.findSlot(BlockPlacer::isSolidBlock);
        if (slot < 0) { restoreSlot(); return; }

        // bottom ring first so every higher block has something to rest on
        BlockPos feet = t.getBlockPos();
        List<BlockPos> spots = new ArrayList<>();
        for (int y = 0; y <= 1; y++) for (Direction d : SIDES) spots.add(feet.up(y).offset(d));
        if (cap.value) spots.add(feet.up(2));

        Box targetBox = t.getBoundingBox(), myBox = mc.player.getBoundingBox();
        List<BlockPos> need = new ArrayList<>();
        for (BlockPos p : spots) {
            if (!mc.world.getBlockState(p).isReplaceable()) continue;       // already filled
            Box b = new Box(p);
            if (b.intersects(targetBox) || b.intersects(myBox)) continue;   // would be rejected / trap yourself
            need.add(p);
        }
        if (need.isEmpty()) { wait = 0; restoreSlot(); return; }
        if (++wait < Pacing.gap(finish.value, spots.size(), delay.intValue())) return;   // paced: one block every finish/N seconds

        int per = Pacing.perStep(finish.value, perTick.intValue());
        int placed = 0;
        for (BlockPos p : need) {
            int cur = BlockPlacer.selected();
            if (!BlockPlacer.select(slot)) return;                  // one swap per tick: try again next tick
            if (restore < 0) restore = cur;
            if (BlockPlacer.place(p) && ++placed >= per) break;
        }
        if (placed > 0) { wait = 0; if (Pacing.paced(finish.value)) restoreSlot(); } else restoreSlot();
    }
}

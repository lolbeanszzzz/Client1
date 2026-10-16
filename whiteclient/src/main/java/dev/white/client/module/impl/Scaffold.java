package dev.white.client.module.impl;

import dev.white.client.util.SwapLimit;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Places a block under your feet as you walk, so you never fall. Uses solid full blocks from the hotbar. */
public class Scaffold extends Module {
    private static final Direction[] SIDES = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

    private final Setting.Bool autoSwitch = add(new Setting.Bool("Auto switch", true));
    private final Setting.Bool swing = add(new Setting.Bool("Swing", true));
    private int previousSlot = -1;

    public Scaffold() { super("Scaffold", Category.MOVEMENT); }

    @Override protected void onEnable() { previousSlot = -1; }

    @Override
    protected void onDisable() {
        if (mc.player != null && previousSlot >= 0) SwapLimit.selectNow(previousSlot);
        previousSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || Freecam.active() || mc.player.isSpectator()) return;

        BlockPos target = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.5, mc.player.getZ());
        if (!mc.world.getBlockState(target).isReplaceable()) return;

        // find a solid neighbour of the target block to place against
        Direction side = null;
        BlockPos support = null;
        for (Direction d : SIDES) {
            BlockPos n = target.offset(d);
            if (!mc.world.getBlockState(n).isReplaceable()) { side = d.getOpposite(); support = n; break; }
        }
        if (support == null) return;   // nothing to build off (e.g. mid-air with no neighbours)

        if (!hasBlockInHand() && !(autoSwitch.value && selectBlock())) return;

        Vec3d hit = Vec3d.ofCenter(support).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, side, support, false));
        if (swing.value) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean hasBlockInHand() { return usable(mc.player.getMainHandStack()); }

    private boolean selectBlock() {
        for (int i = 0; i < 9; i++) {
            if (usable(mc.player.getInventory().getStack(i))) {
                int cur = mc.player.getInventory().getSelectedSlot();
                if (!SwapLimit.select(i)) return false;                // one swap per tick: try again next tick
                if (previousSlot < 0) previousSlot = cur;
                return true;
            }
        }
        return false;
    }

    /** Only plain solid blocks: no chests/furnaces, no sand/gravel, no slabs/stairs/plants. */
    private boolean usable(ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        return !(b instanceof BlockWithEntity) && !(b instanceof FallingBlock)
                && b.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN);
    }
}

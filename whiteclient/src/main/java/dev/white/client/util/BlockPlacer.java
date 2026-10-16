package dev.white.client.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

/** Shared helpers for modules that place blocks / pick hotbar slots. */
public final class BlockPlacer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Direction[] ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP };
    public static final double REACH = 4.5;

    private BlockPlacer() {}

    /** Places the held block at target by clicking an adjacent solid block. Returns false if impossible. */
    public static boolean place(BlockPos target) {
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return false;
        if (!mc.world.getBlockState(target).isReplaceable()) return false;
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target)) > REACH * REACH) return false;
        for (Direction d : ORDER) {
            BlockPos n = target.offset(d);
            if (mc.world.getBlockState(n).isReplaceable()) continue;
            Direction face = d.getOpposite();
            Vec3d hit = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, face, n, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    public static int findSlot(Predicate<ItemStack> wanted) {
        for (int i = 0; i < 9; i++) if (wanted.test(mc.player.getInventory().getStack(i))) return i;
        return -1;
    }

    public static int selected() { return mc.player.getInventory().getSelectedSlot(); }
    /** Selects the slot, one swap per tick for all modules (see SwapLimit). False = refused, try again next tick. */
    public static boolean select(int slot) { return SwapLimit.select(slot); }
    /** Clean-up when a module is switched off: selects the slot even if a swap was already made this tick. */
    public static void selectForce(int slot) { SwapLimit.selectNow(slot); }

    /** Plain solid blocks only: no chests/furnaces, sand/gravel, slabs/stairs/plants. */
    public static boolean isSolidBlock(ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        return !(b instanceof BlockWithEntity) && !(b instanceof FallingBlock)
                && b.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN);
    }

    /** Nearest other living, non-spectator player within range, or null. */
    public static PlayerEntity nearestPlayer(double range) {
        PlayerEntity best = null;
        double bestD = range;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive()) continue;
            double d = mc.player.distanceTo(p);
            if (d <= bestD) { bestD = d; best = p; }
        }
        return best;
    }
}

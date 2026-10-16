package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.BlockPlacer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Auto MLG: when you fall far enough, looks straight down, switches to a hotbar water bucket and places the water just
 * before you land, then scoops it back up and returns to your old slot and view. Needs a water bucket in the hotbar.
 * Does nothing while flying / gliding / riding, in the Nether-style dimensions where water evaporates, or when you
 * would land in water / on a slime block anyway.
 */
public class AutoMLG extends Module {
    private final Setting.Num minFall = add(new Setting.Num("Min fall (blocks)", 4, 3, 30, 1));
    /** Distance from your EYES to the floor at which the water is placed. A bucket only reaches ~4.5 blocks from the eyes. */
    private final Setting.Num place = add(new Setting.Num("Place distance (eyes)", 4.4, 2, 4.5, 0.1));
    private final Setting.Bool pickup = add(new Setting.Bool("Pick water up", true));
    private final Setting.Bool restoreLook = add(new Setting.Bool("Restore view", true));

    private enum State { IDLE, ARMED, PLACED, PICKUP }
    private State state = State.IDLE;
    private int previous = -1, bucketSlot = -1, timer, pendingRestore = -1;
    private float prevPitch;

    public AutoMLG() { super("Auto MLG", Category.PLAYER); }

    @Override protected void onEnable() { state = State.IDLE; }
    @Override protected void onDisable() { finish(); if (pendingRestore >= 0 && mc.player != null) BlockPlacer.selectForce(pendingRestore); pendingRestore = -1; }

    /** Runs before the player ticks, so the new pitch and slot are sent to the server this tick. */
    @Override
    public void onPreTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || state == State.IDLE) return;
        if (state == State.ARMED) {
            p.setPitch(90f);                                 // the server raycasts with the rotation it last received
        }
    }

    @Override
    public void onTick() {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { state = State.IDLE; return; }
        if (pendingRestore >= 0) { if (BlockPlacer.select(pendingRestore)) pendingRestore = -1; return; }   // one swap per tick: the old slot comes back as soon as one is allowed
        if (p.isCreative() || p.isSpectator() || p.getAbilities().flying || p.isGliding() || p.hasVehicle()
                || mc.world.getRegistryKey() == net.minecraft.world.World.NETHER) {
            if (state != State.IDLE) finish();
            return;
        }

        switch (state) {
            case IDLE -> {
                if (p.isOnGround() || p.isTouchingWater() || p.getVelocity().y > -0.5) return;
                if (p.fallDistance < minFall.value) return;
                int slot = BlockPlacer.findSlot(s -> s.isOf(Items.WATER_BUCKET));
                if (slot < 0) return;                         // no water bucket in the hotbar
                int cur = BlockPlacer.selected();
                if (!BlockPlacer.select(slot)) return;        // a swap already happened this tick: try again next tick
                bucketSlot = slot;
                previous = cur;
                prevPitch = p.getPitch();
                p.setPitch(90f);
                state = State.ARMED;
                armedStep(p);                                 // do not waste a tick: the floor may already be in reach
            }
            case ARMED -> armedStep(p);
            case PLACED -> {
                // wait for the landing (or for the water to catch us), then scoop it back up
                if (++timer > 40) { finish(); return; }
                if (p.isOnGround() || p.isTouchingWater()) {
                    timer = 0;
                    state = pickup.value ? State.PICKUP : State.IDLE;
                    if (!pickup.value) finish();
                }
            }
            case PICKUP -> {
                if (++timer > 10) { finish(); return; }
                if (!p.getMainHandStack().isOf(Items.BUCKET)) {
                    if (p.getMainHandStack().isOf(Items.WATER_BUCKET)) { finish(); return; }   // the placement failed, nothing to scoop
                    return;                                   // the server has not confirmed the empty bucket yet
                }
                p.setPitch(90f);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                finish();
            }
            default -> {}
        }
    }

    /**
     * Armed: keep the bucket in hand and the view straight down, and place the water the moment the floor is inside the
     * bucket's reach. Reach is measured from the EYES (about 1.6 blocks above your feet), which is why the old
     * "distance from the feet" check placed too late or out of reach and the water never appeared.
     */
    private void armedStep(PlayerEntity p) {
        if (p.isOnGround() || p.isTouchingWater()) { finish(); return; }
        if (!p.getMainHandStack().isOf(Items.WATER_BUCKET) && !BlockPlacer.select(bucketSlot)) return;   // swap not allowed this tick: wait
        p.setPitch(90f);
        BlockHitResult hit = floor(p);
        if (hit == null) return;                              // nothing below within range yet
        if (!mc.world.getFluidState(hit.getBlockPos()).isEmpty()
                || mc.world.getBlockState(hit.getBlockPos()).isOf(net.minecraft.block.Blocks.SLIME_BLOCK)
                || mc.world.getBlockState(hit.getBlockPos()).isOf(net.minecraft.block.Blocks.HAY_BLOCK)) {
            finish();                                         // water / slime / hay already cushions the landing
            return;
        }
        double reach = Math.min(place.value, p.getBlockInteractionRange() - 0.15);
        if (p.getEyeY() - hit.getPos().y > reach) return;     // still too far for the bucket
        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        timer = 0;
        state = State.PLACED;
    }

    /** First solid block (or fluid) straight below the player, within 6 blocks. */
    private BlockHitResult floor(PlayerEntity p) {
        Vec3d from = new Vec3d(p.getX(), p.getY(), p.getZ());
        Vec3d to = from.subtract(0, 6, 0);
        BlockHitResult r = mc.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.SOURCE_ONLY, p));
        return r.getType() == HitResult.Type.BLOCK ? r : null;
    }

    private void finish() {
        if (mc.player != null) {
            if (previous >= 0 && !BlockPlacer.select(previous)) pendingRestore = previous;
            if (restoreLook.value && state != State.IDLE) mc.player.setPitch(prevPitch);
        }
        previous = -1;
        bucketSlot = -1;
        state = State.IDLE;
    }
}

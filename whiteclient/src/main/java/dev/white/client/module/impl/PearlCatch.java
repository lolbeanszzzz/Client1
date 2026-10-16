package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.BlockPlacer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Pearl Catch: throws an ender pearl, then throws a wind charge so that the charge meets the pearl in the air.
 * Needs an Ender Pearl and a Wind Charge in the hotbar. Switch it on (or press its key): it works out the throw
 * angles and timing from where you are and how you are moving, does both throws, and switches itself off.
 *
 * Planning predicts both flights tick by tick: a pearl starts at 1.5 blocks/tick plus your own motion, loses
 * speed to drag (0.99) and drops by gravity (0.03/tick); a wind charge flies straight at 1.5 blocks/tick. A plan
 * is only used if the two paths pass within ~0.6 blocks of each other, so on a bad position it simply refuses.
 * The plan is made in the very tick the throws happen (not when you press the key), from your real per-tick
 * movement, because the server gives a thrown item your movement of the last tick (not your post-friction speed).
 * Each throw is done inside a single tick: the view is turned, the item is used, and the view goes straight back,
 * so the screen never shows the temporary angle (the use-item packet carries the angle the server needs).
 */
public class PearlCatch extends Module {
    private static final double SPEED = 1.5;
    private static final double PEARL_DRAG = 0.99;
    private static final double PEARL_GRAVITY = 0.03;
    private static final double MAX_MISS_SQ = 0.25;     // 0.5 blocks
    private static final int FLIGHT_TICKS = 30;
    /** Both throws get a random spread of ~0.011 blocks/tick per axis: expected squared miss after t ticks is about 0.00076 * t^2. */
    private static final double SPREAD = 0.00076;
    private static final int GIVE_UP_TICKS = 80;

    private final Setting.Bool useCurrentAim = add(new Setting.Bool("Use current aim", false));
    private final Setting.Num chargeDelay = add(new Setting.Num("Charge delay (ticks)", 0, 0, 10, 1));
    private final Setting.Bool showChat = add(new Setting.Bool("Chat messages", true));

    private enum State { IDLE, THROW_PEARL, WAIT_CHARGE, RESTORE }
    private State state = State.IDLE;
    private Plan plan;
    private Vec3d mv = Vec3d.ZERO;     // how far the player moved during the last tick (what the server hands to thrown items)
    private int meetTick;              // tick of the closest approach found by the last evaluate()
    private int pearlSlot = -1, chargeSlot = -1, savedSlot = -1, elapsed;

    public PearlCatch() { super("Pearl Catch", Category.PLAYER); }

    @Override
    protected void onEnable() {
        state = State.IDLE;
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { setEnabled(false); return; }
        pearlSlot = BlockPlacer.findSlot(s -> s.isOf(Items.ENDER_PEARL));
        chargeSlot = BlockPlacer.findSlot(s -> s.isOf(Items.WIND_CHARGE));
        if (pearlSlot < 0 || chargeSlot < 0) { fail("needs an Ender Pearl and a Wind Charge in the hotbar"); return; }
        if (mc.player.getItemCooldownManager().isCoolingDown(p.getInventory().getStack(pearlSlot))) { fail("the Ender Pearl is on cooldown"); return; }
        savedSlot = BlockPlacer.selected();
        plan = null;
        elapsed = 0;
        state = State.THROW_PEARL;      // the plan is made at the moment of the throw (see onTick)
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && savedSlot >= 0) BlockPlacer.selectForce(savedSlot);
        state = State.IDLE;
        plan = null;
        pearlSlot = chargeSlot = savedSlot = -1;
    }

    @Override
    public void onTick() {
        PlayerEntity p = mc.player;
        if (state == State.IDLE) return;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null || p.isSpectator()) {
            setEnabled(false);
            return;
        }
        if (state == State.RESTORE) {       // one swap per tick: the old slot comes back the tick after the last throw
            if (savedSlot < 0 || BlockPlacer.select(savedSlot)) setEnabled(false);
            return;
        }
        if (state == State.THROW_PEARL) {
            // plan from the state the server will see when it handles the throw: this tick's position and movement
            Vec3d now = p.getLerpedPos(1.0f), before = p.getLerpedPos(0.0f);
            mv = new Vec3d(now.x - before.x, now.y - before.y, now.z - before.z);
            int delay = Math.max(1, chargeDelay.intValue());   // one swap per tick: the charge goes out at least a tick after the pearl
            plan = useCurrentAim.value ? currentAimPlan(p, delay) : upwardPlan(p, delay);
            if (plan == null) { fail("no good way to catch the pearl from here (stand still for a moment, or change the charge delay)"); return; }
            if (!fire(pearlSlot, plan.pearlYaw, plan.pearlPitch)) return;   // a swap already happened this tick: plan again next tick
            elapsed = 0;
            state = State.WAIT_CHARGE;
            return;
        }
        // WAIT_CHARGE: the charge goes out at the end of the tick that is 'delay' ticks after the pearl's tick
        elapsed++;
        if (elapsed > GIVE_UP_TICKS) { state = State.RESTORE; return; }
        if (elapsed >= plan.delay && fire(chargeSlot, plan.chargeYaw, plan.chargePitch)) state = State.RESTORE;
    }

    /** Selects the slot (one swap per tick), uses the item with the given view angles, then puts the view back. False = no swap allowed this tick. */
    private boolean fire(int slot, float yaw, float pitch) {
        PlayerEntity p = mc.player;
        if (!BlockPlacer.select(slot)) return false;             // one swap per tick: not now
        float oldYaw = p.getYaw(), oldPitch = p.getPitch();
        p.setYaw(yaw);
        p.setPitch(pitch);
        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);   // the packet carries this yaw/pitch
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(oldYaw);
        p.setPitch(oldPitch);
        return true;                                             // the slot goes back in the RESTORE tick
    }

    private void fail(String why) {
        if (showChat.value && mc.player != null) {
            mc.player.sendMessage(Text.literal("[Pearl Catch] ").formatted(Formatting.GOLD)
                    .append(Text.literal(why).formatted(Formatting.WHITE)), false);
        }
        setEnabled(false);
    }

    // ---------------------------------------------------------------- planning

    private record Plan(float pearlYaw, float pearlPitch, float chargeYaw, float chargePitch, int delay, double miss) {}

    /** Pearl goes up (a little forward/back), the charge goes up too and meets it. */
    private Plan upwardPlan(PlayerEntity p, int delay) {
        float ref = p.getYaw();
        Vec3d v = mv;
        float[] pearlBases;
        if (v.x * v.x + v.z * v.z < 0.003) {
            pearlBases = new float[]{ref};
        } else {
            float moveYaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
            pearlBases = Math.abs(MathHelper.wrapDegrees(moveYaw - ref)) < 20f ? new float[]{ref} : new float[]{ref, moveYaw};
        }
        Plan best = null;
        double bestScore = Double.MAX_VALUE;
        for (float pPitch = -90f; pPitch <= -76f; pPitch += 0.5f) {
            for (float base : pearlBases) {
                for (float pOff = -20f; pOff <= 20f; pOff += 2.5f) {
                    float pYaw = base + pOff;
                    for (float cPitch = -90f; cPitch <= -86f; cPitch += 1f) {
                        for (float cOff = -20f; cOff <= 20f; cOff += 5f) {
                            float cYaw = ref + cOff;
                            double miss = evaluate(p, pYaw, pPitch, cYaw, cPitch, delay);
                            if (miss > MAX_MISS_SQ) continue;
                            // prefer a close meeting, an EARLY one (both throws get a small random spread that grows
                            // with flight time), and angles that stay near where you already face
                            double score = miss + SPREAD * meetTick * meetTick + Math.abs(MathHelper.wrapDegrees(cYaw - ref)) * 0.01
                                    + Math.abs(MathHelper.wrapDegrees(pYaw - ref)) * 0.003 + Math.abs(cPitch + 90f) * 0.03;
                            if (score < bestScore) { bestScore = score; best = new Plan(pYaw, pPitch, cYaw, cPitch, delay, miss); }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** The charge goes where you are looking now; the pearl angle and the delay are searched. */
    private Plan currentAimPlan(PlayerEntity p, int minDelay) {
        float cYaw = p.getYaw(), cPitch = p.getPitch();
        int first = Math.max(3, minDelay);
        Plan best = null;
        double bestScore = Double.MAX_VALUE;
        for (int delay = first; delay <= first + 12; delay++) {
            for (float pPitch = -90f; pPitch <= 60f; pPitch += 2.5f) {
                for (float off = -180f; off < 180f; off += 5f) {
                    float pYaw = cYaw + off;
                    double miss = evaluate(p, pYaw, pPitch, cYaw, cPitch, delay);
                    if (miss > MAX_MISS_SQ) continue;
                    double score = miss + SPREAD * meetTick * meetTick + Math.abs(off) * 0.0005 + delay * 0.0001;
                    if (score < bestScore) { bestScore = score; best = new Plan(pYaw, pPitch, cYaw, cPitch, delay, miss); }
                }
            }
        }
        return best;
    }

    /**
     * Smallest squared distance between the pearl and the charge once the charge is in flight, or +infinity when
     * the plan is not usable. Pearl is thrown now; the charge is thrown 'delay' ticks later from where you will be.
     */
    private double evaluate(PlayerEntity p, float pYaw, float pPitch, float cYaw, float cPitch, int delay) {
        Vec3d pd = dir(pYaw, pPitch), cd = dir(cYaw, cPitch);
        Vec3d v = mv;
        boolean air = !p.isOnGround();

        // pearl: leaves 0.1 below the eyes, inherits your motion (vertical only while airborne)
        double px = p.getX(), py = p.getEyeY() - 0.1, pz = p.getZ();
        double pvx = pd.x * SPEED + v.x, pvy = pd.y * SPEED + (air ? v.y : 0.0), pvz = pd.z * SPEED + v.z;

        // where you (and so the charge) will be when the charge is thrown
        double[] o = originAfter(p, delay);
        double cx = o[0], cy = o[1], cz = o[2];
        double cvx = cd.x * SPEED + o[3], cvy = cd.y * SPEED + (air ? o[4] : 0.0), cvz = cd.z * SPEED + o[5];

        double closest = Double.POSITIVE_INFINITY;
        for (int t = 1; t <= delay + FLIGHT_TICKS; t++) {
            pvy -= PEARL_GRAVITY;
            pvx *= PEARL_DRAG; pvy *= PEARL_DRAG; pvz *= PEARL_DRAG;
            px += pvx; py += pvy; pz += pvz;
            if (t <= delay) continue;               // the charge is not in the air yet
            cx += cvx; cy += cvy; cz += cvz;
            double dx = px - cx, dy = py - cy, dz = pz - cz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < closest) { closest = d; meetTick = t; }
        }
        return closest;
    }

    /**
     * {x, eyeY, z, vx, vy, vz} of the player after 'ticks' ticks, assuming no input change. vx/vy/vz is the movement
     * during the last of those ticks (at 0 ticks: the last real tick), which is what a thrown item inherits.
     */
    private double[] originAfter(PlayerEntity p, int ticks) {
        Vec3d v = mv;
        double x = p.getX(), y = p.getEyeY(), z = p.getZ(), vx = v.x, vy = v.y, vz = v.z;
        boolean air = !p.isOnGround();
        for (int i = 0; i < ticks; i++) {
            if (air) { vy = (vy - 0.08) * 0.98; vx *= 0.91; vz *= 0.91; y += vy; }
            else { vx *= 0.546; vz *= 0.546; }
            x += vx; z += vz;
        }
        return new double[]{x, y, z, vx, air ? vy : 0.0, vz};
    }

    private static Vec3d dir(float yaw, float pitch) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        return new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
    }
}

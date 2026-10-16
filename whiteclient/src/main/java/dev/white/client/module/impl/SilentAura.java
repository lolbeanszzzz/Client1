package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.CombatControl;
import dev.white.client.util.CombatReach;
import dev.white.client.util.Friends;
import dev.white.client.util.Rotations;
import dev.white.client.util.SmoothLook;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Silent Aura rewritten around the Vape SilentAura design:
 *
 *  Modes: IDLE → ATTACKING → FLICKING_AWAY → IDLE
 *  Adaptive rotation speed (log-normal multiplier ~1.4–3.0, soft when very close)
 *  Aim jitter that slowly drifts on the body
 *  Stable target pick (nearest, ties by entity id)
 *  Optional real-view look-at
 *  Integrates with AutoWeapon (weapon pick, axe-on-shield / mace-smash hits) + AttackPackets
 *
 * Server sees silent yaw/pitch via {@link Rotations}; your camera is untouched
 * (ClientPlayerEntityMixin restores real angles after the move packet).
 */
public class SilentAura extends Module {

    public enum Mode { IDLE, FLICKING_AWAY, ATTACKING }

    // Vape-aligned settings
    private final Setting.Num range = add(new Setting.Num("Range", 3.0, 1.0, 3.0, 0.1));
    private final Setting.Num rotateRange = add(new Setting.Num("Rotate range", 4.5, 1.0, 8.0, 0.1));
    private final Setting.Num maxAngle = add(new Setting.Num("Max angle", 120, 10, 360, 5));
    private final Setting.Num extraSwing = add(new Setting.Num("Extra swing distance", 1.0, 0.0, 3.0, 0.1));
    private final Setting.Bool players = add(new Setting.Bool("Players", true));
    private final Setting.Bool mobs = add(new Setting.Bool("Other mobs", false));
    private final Setting.Bool requireMouseDown = add(new Setting.Bool("Require mouse down", false));
    private final Setting.Bool switchTargets = add(new Setting.Bool("Switch targets", false));
    private final Setting.Bool perfectSwing = add(new Setting.Bool("Perfect swing", true));
    private final Setting.Bool instantHits = add(new Setting.Bool("Instant axe/mace hits", true));
    private final Setting.Num minCharge = add(new Setting.Num("Min charge", 0.95, 0.1, 1.0, 0.05));
    private final Setting.Bool shieldCheck = add(new Setting.Bool("Shield check", false));
    private final Setting.Num aimSpeed = add(new Setting.Num("Aim speed (deg/tick)", 14, 3, 60, 1));
    private final Setting.Num returnSpeed = add(new Setting.Num("Flick-back speed (deg/tick)", 12, 3, 60, 1));
    private final Setting.Num smooth = add(new Setting.Num("Smoothing %", 35, 0, 100, 5));
    private final Setting.Num aimHeight = add(new Setting.Num("Aim height %", 70, 10, 100, 5));
    private final Setting.Num jitter = add(new Setting.Num("Aim jitter", 0.15, 0.0, 0.6, 0.05));
    private final Setting.Num vertSpeed = add(new Setting.Num("Vertical speed %", 70, 10, 100, 5));
    private final Setting.Bool randomAim = add(new Setting.Bool("Random aim", true));
    private final Setting.Bool lookAt = add(new Setting.Bool("Look at target", false));
    private final Setting.Num lookSpeed = add(new Setting.Num("  Look speed (deg/tick)", 20, 2, 60, 1));
    private final Setting.Bool flick = add(new Setting.Bool("Flick away after hit", true));
    private final Setting.Num deadZone = add(new Setting.Num("Dead zone (deg)", 0.35, 0.0, 3.0, 0.05));
    private final Setting.Bool debug = add(new Setting.Bool("Debug (chat log)", false));

    private Mode mode = Mode.IDLE;
    private boolean spoof;
    private float yaw, pitch;               // server-side angles
    private LivingEntity target;
    private int flickTicks;
    private int ticks, lastHitTick = -100;
    private final Random rng = new Random();

    // adaptive rotation state
    private double velYaw, velPitch;
    private LivingEntity lastTarget;

    // aim jitter (Vape SilentAuraAimJitter style)
    private final AimJitter jitterX = new AimJitter(-1, 1);
    private final AimJitter jitterY = new AimJitter(-1, 1);
    private final AimJitter jitterZ = new AimJitter(-1, 1);

    public SilentAura() { super("Silent Aura", Category.COMBAT); }

    @Override protected void onEnable() { toIdle(); }
    @Override protected void onDisable() { toIdle(); target = null; }

    private void toIdle() {
        mode = Mode.IDLE;
        spoof = false;
        flickTicks = 0;
        Rotations.release(this);
        velYaw = velPitch = 0;
        lastTarget = null;
    }

    // ----- tick ---------------------------------------------------------------

    /** Before movement packet: pick target, update silent rotation. */
    @Override
    public void onPreTick() {
        ticks++;
        if (mc.player == null || mc.world == null) { toIdle(); target = null; return; }
        if (CombatControl.blocked(this)) { toIdle(); return; }
        if (mc.currentScreen != null || Freecam.active() || mc.player.isUsingItem() || mc.player.isSpectator()) {
            toIdle(); target = null; return;
        }
        if (requireMouseDown.value) {
            long win = mc.getWindow() == null ? 0 : mc.getWindow().getHandle();
            if (win == 0 || org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                toIdle(); target = null; return;
            }
        }

        float realYaw = mc.player.getYaw(), realPitch = mc.player.getPitch();
        if (!spoof) { yaw = realYaw; pitch = realPitch; }

        target = pickTarget();
        if (target != lastTarget) {
            velYaw = velPitch = 0;
            lastTarget = target;
            jitterX.reset(); jitterY.reset(); jitterZ.reset();
        }

        if (lookAt.value && target != null) SmoothLook.step(target, lookSpeed.value);

        float need = perfectSwing.value ? (float) minCharge.value : 0.6f;
        boolean charged = target != null && (
                AutoWeapon.progress() >= need
                || instant(target)
                || AutoWeapon.bypass(target));

        if (mode == Mode.FLICKING_AWAY && flickTicks > 0) flickTicks--;
        if (charged && !(mode == Mode.FLICKING_AWAY && flickTicks > 0)) mode = Mode.ATTACKING;
        else if (!charged && mode == Mode.ATTACKING) {
            mode = Mode.FLICKING_AWAY;
            flickTicks = 0;
        }

        if (mode == Mode.IDLE) return;

        float wantYaw, wantPitch;
        if (mode == Mode.ATTACKING) {
            jitterX.update(); jitterY.update(); jitterZ.update();
            float[] a = anglesTo(aimPoint(target));
            wantYaw = a[0];
            wantPitch = a[1];
        } else {
            wantYaw = realYaw;
            wantPitch = realPitch;
        }

        spoof = true;
        steer(wantYaw, wantPitch, mode == Mode.ATTACKING);
        if (!Rotations.set(this, yaw, pitch)) { toIdle(); return; }

        if (mode == Mode.FLICKING_AWAY && flickTicks <= 0
                && Math.abs(MathHelper.wrapDegrees(realYaw - yaw)) < 0.5f
                && Math.abs(realPitch - pitch) < 0.5f) {
            toIdle();
        }
    }

    /**
     * After movement packet: only when we actually attack, send ONE look packet
     * then the hit. Sending look every tick while aiming caused Grim TickTimer
     * (extra packets per tick) and PacketOrderD desync with transactions.
     */
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (debug.value && ticks % 10 == 0 && target != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§7[aura] §f" + target.getName().getString()
                    + " d=" + String.format("%.2f", CombatReach.eyeDistance(target)) + " mode=" + mode + " spoof=" + spoof
                    + " aimed=" + aimedAt(target, true) + " instant=" + instant(target) + " | " + AutoWeapon.why(target)), false);
        }
        if (mode != Mode.ATTACKING || !spoof || target == null || !target.isAlive()) return;

        // Auto Weapon: right weapon for this target, picked ahead of the hit (the hit itself waits one tick, see below)
        AutoWeapon.onTarget(target);
        boolean weaponStep = AutoWeapon.bypass(target);     // axe on a shield / mace smash: no charge wait

        if (!CombatReach.inReach(target, reach())) return;
        // only a shield-break axe / mace smash skips the charge wait (weaponStep); everything else waits for Min charge
        if (!weaponStep && AutoWeapon.progress() < minCharge.value) return;
        if (!aimedAt(target, true)) return;   // silent aim: a look packet with these angles goes out right before the hit
        if (!AutoWeapon.readyToAttack()) return;   // the slot was changed this tick: hit next tick

        // Queue the hit for the START of next tick (before movement). Do not inject
        // extra Look packets here — they desync Grim transactions (PacketOrderD / TickTimer).
        // Server raycast uses the real look from movement; keep crosshair near the target.
        boolean followUp = AutoWeapon.followUpExpected(target);   // axe on a shield, mace hit comes next: keep the aim
        AttackPackets.hit(target, e -> e instanceof LivingEntity le && le.isAlive() && spoof
                && CombatReach.inReach(le, reach()) && aimedAt(le, true));
        lastHitTick = ticks;
        if (flick.value && !followUp) {
            mode = Mode.FLICKING_AWAY;
            flickTicks = 2 + rng.nextInt(3);
        }
    }

    // ----- target pick --------------------------------------------------------

    private LivingEntity pickTarget() {
        // Keep current target while in hit cooldown unless Switch is on (Vape behaviour)
        if (target != null && target.isAlive() && valid(target)
                && mc.player.distanceTo(target) <= rotateRange.value + extraSwing.value
                && withinAngle(target)
                && !switchTargets.value
                && mode == Mode.ATTACKING) {
            return target;
        }
        LivingEntity best = null;
        double bestD = rotateRange.value + extraSwing.value;
        int bestId = Integer.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !valid(le)) continue;
            double d = mc.player.distanceTo(le);
            if (d > bestD) continue;
            if (!withinAngle(le)) continue;
            int id = le.getId();
            // nearer wins; equal distance → lower entity id (stable, like Vape comparator)
            if (d < bestD - 1e-6 || (Math.abs(d - bestD) < 1e-6 && id < bestId)) {
                bestD = d;
                bestId = id;
                best = le;
            }
        }
        return best;
    }

    /** True when target is inside Max angle from the real camera (Vape maxAngle). */
    private boolean withinAngle(LivingEntity e) {
        float[] a = anglesTo(aimPoint(e));
        float dy = Math.abs(MathHelper.wrapDegrees(a[0] - mc.player.getYaw()));
        float dp = Math.abs(a[1] - mc.player.getPitch());
        return dy <= maxAngle.value && dp <= maxAngle.value;
    }

    private boolean valid(LivingEntity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (Friends.shield(e)) return false;
        if (e instanceof PlayerEntity p) {
            if (!players.value || p.isSpectator()) return false;
            if (shieldCheck.value && AutoWeapon.wouldBlockHit(p)
                    && !AutoWeapon.shieldBreakActive() && !AutoWeapon.bypass(p)) {
                return false;
            }
            return true;
        }
        return mobs.value;
    }

    // ----- aim point + jitter -------------------------------------------------

    private Vec3d aimPoint(LivingEntity t) {
        double j = jitter.value;
        double hx = t.getWidth() * 0.45 * j * jitterX.get();
        double hz = t.getWidth() * 0.45 * j * jitterZ.get();
        double hy = t.getHeight() * (aimHeight.value / 100.0)
                  + t.getHeight() * 0.25 * j * jitterY.get();
        hy = MathHelper.clamp(hy, t.getHeight() * 0.08, t.getHeight() * 0.95);
        return new Vec3d(t.getX() + hx, t.getY() + hy, t.getZ() + hz);
    }

    private float[] anglesTo(Vec3d to) {
        Vec3d eye = mc.player.getEyePos();
        double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float y = h > 0.01 ? (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0) : yaw;
        float p = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, Math.max(h, 1e-4))), -90.0, 90.0);
        return new float[]{y, p};
    }

    // ----- adaptive rotation (Vape SilentAuraRotationController) --------------

    /**
     * Log-normal speed multiplier in ~[1.4, 3.0], soft when target is inside 0.8 blocks.
     */
    private double adaptiveSpeed(boolean attacking) {
        double base = (attacking ? aimSpeed.value : returnSpeed.value);
        // Wide log-normal + extra uniform jitter so Vulcan Aim Constant doesn't see flat rates
        double u1 = Math.max(1e-4, rng.nextDouble());
        double u2 = rng.nextDouble();
        double gaussian = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(Math.PI * 2 * u2);
        double mult = Math.exp(0.45 + 0.40 * gaussian);
        mult = MathHelper.clamp(mult, 0.7, 3.2);
        mult *= 0.75 + rng.nextDouble() * 0.55; // per-call jitter
        double reach = base * mult;
        if (attacking && target != null) {
            double d = mc.player.distanceTo(target);
            if (d < 0.8) reach *= Math.max(0.1, d / 0.8);
            // ease by angular error would need more state; distance falloff helps Linear
            reach *= 0.55 + 0.45 * MathHelper.clamp(d / 3.0, 0, 1);
        }
        return reach;
    }

    private void steer(double wantYaw, double wantPitch, boolean attacking) {
        final double dt = 0.05;
        double errYaw = MathHelper.wrapDegrees(wantYaw - yaw);
        double errPitch = wantPitch - pitch;
        double combined = Math.hypot(errYaw, errPitch);

        if (combined < deadZone.value) {
            velYaw *= 0.5;
            velPitch *= 0.5;
            return;
        }

        double cap = adaptiveSpeed(attacking) * 20.0; // deg/s
        double maxYaw = cap;
        double maxPitch = cap * vertSpeed.value / 100.0;

        // proportional + soft accel (smoothing)
        double gain = 8.0 + 10.0 * (1.0 - smooth.value / 100.0); // higher smooth → lower gain
        double wantVelYaw = MathHelper.clamp(errYaw * gain, -maxYaw, maxYaw);
        double wantVelPitch = MathHelper.clamp(errPitch * gain, -maxPitch, maxPitch);

        double tau = 0.02 + 0.14 * smooth.value / 100.0;
        double blend = 1.0 - Math.exp(-dt / tau);
        velYaw += (wantVelYaw - velYaw) * blend;
        velPitch += (wantVelPitch - velPitch) * blend;

        double stepYaw = velYaw * dt;
        double stepPitch = velPitch * dt;

        // don't hard-overshoot
        if (Math.signum(stepYaw) == Math.signum(errYaw) && Math.abs(stepYaw) > Math.abs(errYaw)) {
            stepYaw = errYaw;
            velYaw = stepYaw / dt;
        }
        if (Math.signum(stepPitch) == Math.signum(errPitch) && Math.abs(stepPitch) > Math.abs(errPitch)) {
            stepPitch = errPitch;
            velPitch = stepPitch / dt;
        }

        yaw += (float) stepYaw;
        pitch = MathHelper.clamp(pitch + (float) stepPitch, -90f, 90f);
    }

    /** Range setting, never above the vanilla 3.0. */
    private double reach() { return Math.min(range.value, CombatReach.MAX); }

    /**
     * "Instant axe/mace hits": the target calls for a shield-break axe or a mace smash (even if the swap has not happened
     * yet), so start aiming without waiting for the charge. Not true just because you hold an axe or a mace: ordinary
     * axe/mace hits still wait for Min charge.
     */
    private boolean instant(LivingEntity t) {
        return instantHits.value && t != null && AutoWeapon.wantsStep(t);
    }

    /**
     * Every hit (slam or plain) goes out with a look packet carrying the SILENT angles, so the silent aim has to be
     * on the target, not your real camera.
     * Hitbox is the exact vanilla box (no extra margin), ray length at most 3.0.
     */
    private boolean aimedAt(LivingEntity t, boolean silent) {
        boolean useSilent = silent && Rotations.active();
        float y = useSilent ? Rotations.yaw() : mc.player.getYaw();
        float p = useSilent ? Rotations.pitch() : mc.player.getPitch();
        return CombatReach.rayHits(t, y, p, reach());
    }

    // ----- jitter helper (Vape SilentAuraAimJitter) ---------------------------

    private static final class AimJitter {
        private final double min, max;
        private double current, target;
        private long nextMs;
        private final Random r = ThreadLocalRandom.current();

        AimJitter(double min, double max) {
            this.min = min;
            this.max = max;
            reset();
        }

        void reset() {
            current = target = min + r.nextDouble() * (max - min);
            nextMs = System.currentTimeMillis() + 100 + r.nextInt(900);
        }

        void update() {
            long now = System.currentTimeMillis();
            if (now >= nextMs) {
                target = min + r.nextDouble() * (max - min);
                nextMs = now + 100 + r.nextInt(900);
            }
            double step = 0.01 + r.nextDouble() * 0.05;
            if (target > current) {
                current = Math.min(target, current + step);
            } else if (target < current) {
                current = Math.max(target, current - step);
            }
        }

        double get() { return current; }
    }
}

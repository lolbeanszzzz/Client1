package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.Friends;
import dev.white.client.util.Look;
import dev.white.client.util.Projector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Aim Assist aligned with Vape AimAssist (Simple-style smooth aim + Adaptive humanization):
 *  - smoothed aim point + motion lead
 *  - aim-strength fade near target
 *  - bias + error-velocity + flick boost
 *  - multi-band noise + pitch drift
 *  - airborne aim-height blend
 *  - sticky target lock
 *  - mouse-path application (changeLookDirection)
 *
 * Target pick @ 20 Hz (onTick). Steering every rendered frame (onHud).
 */
public class AimAssist extends Module {
    private static final long CLICK_MEMORY_MS = 350;
    private static final double MOUSE_UNIT = 0.15;

    private final Setting.Num range = add(new Setting.Num("Range", 6.0, 1.0, 10.0, 0.5));
    private final Setting.Num fov = add(new Setting.Num("Max angle", 180, 10, 360, 5));
    private final Setting.Num strength = add(new Setting.Num("Strength %", 75, 0, 100, 1)); // 0 = legit humanized, 100 = blatant lock
    private final Setting.Num horizontalSpeed = add(new Setting.Num("Horizontal speed", 70, 5, 100, 1));
    private final Setting.Num verticalSpeed = add(new Setting.Num("Vertical speed", 60, 5, 100, 1));
    private final Setting.Num smoothing = add(new Setting.Num("Smoothing %", 25, 0, 100, 5));
    private final Setting.Num deadZone = add(new Setting.Num("Dead zone (deg)", 0.15, 0.0, 3.0, 0.05));
    private final Setting.Num aimHeight = add(new Setting.Num("Aim height %", 65, 10, 100, 5));
    private final Setting.Bool closestPoint = add(new Setting.Bool("Closest point", true));
    private final Setting.Bool vertical = add(new Setting.Bool("Aim vertically", false));
    private final Setting.Bool players = add(new Setting.Bool("Players", true));
    private final Setting.Bool mobs = add(new Setting.Bool("Other mobs", false));
    private final Setting.Bool needAttack = add(new Setting.Bool("Require mouse down", true));
    private final Setting.Bool needSight = add(new Setting.Bool("Need line of sight", false));
    private final Setting.Bool pauseUsing = add(new Setting.Bool("Pause while using item", true));
    private final Setting.Bool yieldMouse = add(new Setting.Bool("Yield to mouse", true));
    private final Setting.Bool stickyTarget = add(new Setting.Bool("Sticky target", true));
    private final Setting.Bool strafeIncrease = add(new Setting.Bool("Strafe increase", true));
    private final Setting.Num noise = add(new Setting.Num("Noise %", 28, 0, 50, 2));
    private final Setting.Bool showStatus = add(new Setting.Bool("Show status", true));

    private long lastAttack;
    private String status = "";
    private int statusColor = Look.MUTED;

    private Entity target;
    private int loseGrace;          // ticks to keep target when briefly out of FOV / LOS
    private static final int LOSE_GRACE_TICKS = 8;

    // frame timing
    private long lastFrameNs;
    private long noiseStartNs = System.nanoTime();

    // smoothed aim point + lead
    private double aimX, aimY, aimZ;
    private double leadX, leadY, leadZ;
    private boolean aimPointInit;

    // controller
    private double aimStrength;
    private double yawBias, pitchBias;
    private double yawVelocity, pitchVelocity;       // error velocity
    private double yawAccel, pitchAccel;             // smoothed target angular rate
    private double lastErrYaw, lastErrPitch;
    private double lastTargetYaw, lastTargetPitch;
    private double lastPlayerYaw, lastPlayerPitch;
    private boolean initialized;

    private double yawFlickTicks, pitchFlickTicks;
    private double lastYawSign, lastPitchSign;

    private double appliedYaw, appliedPitch;
    private boolean haveApplied;
    private double yieldYaw, yieldPitch;

    // airborne
    private double airFactor, lastGroundEyeY, lastEyeY, verticalVelocity;

    // pitch drift
    private double driftPos, driftVel, driftTarget, driftNoise;
    private long driftNextNs;

    // soft overshoot state
    private double overshoot;

    // Vulcan Aim Linear/Constant/Analysis mitigation
    private double humanMul = 1.0;          // slow-changing random speed multiplier
    private long humanNextNs;
    private double reactDelay;              // seconds before we start correcting a new target
    private long reactUntilNs;
    private double pathCurve;               // signed arc bias that drifts
    private double lastStepYaw, lastStepPitch;
    private int pauseFrames;                // occasional micro-pause

    public AimAssist() { super("Aim Assist", Category.COMBAT); }

    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { status = ""; target = null; reset(); }

    private void reset() {
        lastFrameNs = 0;
        aimPointInit = false;
        aimStrength = 0;
        yawBias = pitchBias = 0;
        yawVelocity = pitchVelocity = 0;
        yawAccel = pitchAccel = 0;
        lastErrYaw = lastErrPitch = 0;
        lastTargetYaw = lastTargetPitch = 0;
        lastPlayerYaw = lastPlayerPitch = 0;
        initialized = false;
        yawFlickTicks = pitchFlickTicks = 0;
        lastYawSign = lastPitchSign = 0;
        haveApplied = false;
        yieldYaw = yieldPitch = 0;
        airFactor = lastGroundEyeY = lastEyeY = verticalVelocity = 0;
        driftPos = driftVel = driftTarget = driftNoise = 0;
        driftNextNs = 0;
        overshoot = 0;
        noiseStartNs = System.nanoTime();
        humanMul = 1.0;
        humanNextNs = 0;
        reactDelay = 0;
        reactUntilNs = 0;
        pathCurve = 0;
        lastStepYaw = lastStepPitch = 0;
        pauseFrames = 0;
    }

    private boolean mouseDown() {
        return mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void say(String s, int color) { status = s; statusColor = color; }

    @Override
    public void onHud(DrawContext g) {
        if (mc.player == null || mc.currentScreen != null) { lastFrameNs = 0; return; }
        if (mouseDown()) lastAttack = System.currentTimeMillis();
        steer();
        if (showStatus.value && !status.isEmpty()) {
            Look.tagClamped(g, mc.textRenderer, status, Projector.width() / 2, Projector.height() / 2 + 22, statusColor);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) { target = null; return; }
        if (mc.currentScreen != null) { softDrop(""); return; }
        if (mouseDown()) lastAttack = System.currentTimeMillis();
        if (Freecam.active() || Freelook.active() || mc.player.isSpectator()) {
            softDrop("Aim: paused (freecam/freelook)"); return;
        }
        if (pauseUsing.value && mc.player.isUsingItem()) { softDrop("Aim: paused (using item)"); return; }
        if (needAttack.value && System.currentTimeMillis() - lastAttack > CLICK_MEMORY_MS) {
            softDrop("Aim: hold click"); return;
        }

        // Hard sticky: never switch, only drop when dead / far
        if (stickyTarget.value && target != null) {
            if (valid(target) && mc.player.distanceTo(target) <= range.value + 1.5) {
                loseGrace = LOSE_GRACE_TICKS;
                say("Aim: " + target.getName().getString() + " [lock]", Look.GOOD);
                return;
            }
            softDrop("Aim: lock lost");
            return;
        }

        // Keep current target if still valid (range + alive) even if briefly outside FOV / LOS
        if (target != null && valid(target) && mc.player.distanceTo(target) <= range.value + 1.0) {
            Vec3d eye = mc.player.getEyePos();
            float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
            double half = fov.value / 2.0;
            Vec3d p = aimPointRaw(target, eye.y);
            double[] a = angles(eye, p);
            double ang = Math.hypot(MathHelper.wrapDegrees(a[0] - yaw), a[1] - pitch);
            boolean los = !needSight.value || mc.player.canSee(target);
            boolean inFov = ang <= half * 1.35; // 35% FOV grace
            if (los && inFov) {
                loseGrace = LOSE_GRACE_TICKS;
                say("Aim: " + target.getName().getString(), Look.GOOD);
                return;
            }
            // brief obstruction / edge of FOV — keep tracking
            if (loseGrace > 0) {
                loseGrace--;
                say("Aim: " + target.getName().getString() + " ~", Look.WARN);
                return;
            }
        }

        Vec3d eye = mc.player.getEyePos();
        float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
        double half = fov.value / 2.0;

        Entity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!valid(e)) continue;
            if (mc.player.distanceTo(e) > range.value) continue;
            if (needSight.value && !mc.player.canSee(e)) continue;
            Vec3d p = aimPointRaw(e, eye.y);
            double[] a = angles(eye, p);
            double ang = Math.hypot(MathHelper.wrapDegrees(a[0] - yaw), a[1] - pitch);
            if (ang > half || ang >= bestAngle) continue;
            bestAngle = ang;
            best = e;
        }

        if (best == null) {
            if (target != null && loseGrace > 0) {
                loseGrace--;
                say("Aim: " + target.getName().getString() + " ~", Look.WARN);
                return;
            }
            softDrop("Aim: no target");
            return;
        }

        // Only hard-reset controller when switching to a *different* entity
        if (best != target) {
            reset();
            loseGrace = LOSE_GRACE_TICKS;
        }
        target = best;
        say("Aim: " + best.getName().getString() + (stickyTarget.value ? " [lock]" : ""), Look.GOOD);
    }

    /** Drop target but keep controller warm so re-acquire is instant. */
    private void softDrop(String why) {
        target = null;
        loseGrace = 0;
        // do NOT wipe vel / aim point — avoids the "works then dies" feel
        lastFrameNs = 0;
        say(why, why.contains("paused") ? Look.WARN : Look.MUTED);
    }

    private void drop(String why) {
        target = null;
        reset();
        say(why, why.contains("paused") ? Look.WARN : Look.MUTED);
    }

    // -------------------------------------------------------------------------
    // Per-frame steer (Vape-style control loop)
    // -------------------------------------------------------------------------

    private void steer() {
        long now = System.nanoTime();
        double dt = lastFrameNs == 0 ? 1.0 / 60.0 : (now - lastFrameNs) / 1.0e9;
        lastFrameNs = now;
        dt = MathHelper.clamp(dt, 0.008, 0.12);

        Entity t = target;
        if (t == null || !t.isAlive() || mc.player == null || mc.world == null) { return; }
        if (mc.player.distanceTo(t) > range.value + 2.0) { return; }

        float pt = Projector.tickProgress;
        Vec3d me = mc.player.getLerpedPos(pt);
        double eyeOff = mc.player.getEyePos().y - mc.player.getY();
        double eyeY = me.y + eyeOff;
        Vec3d eye = new Vec3d(me.x, eyeY, me.z);

        // airborne factor
        if (lastEyeY != 0) {
            verticalVelocity += ((eyeY - lastEyeY) / dt - verticalVelocity) * 0.65;
        }
        lastEyeY = eyeY;
        if (mc.player.isOnGround()) {
            lastGroundEyeY = eyeY;
            airFactor *= 0.35;
        } else {
            if (lastGroundEyeY == 0) lastGroundEyeY = eyeY;
            double height = Math.max(0, eyeY - lastGroundEyeY);
            double vAir = Math.min(0.7, Math.max(0, verticalVelocity) * 0.34);
            double hAir = Math.min(0.82, height * 0.58);
            airFactor = Math.max(airFactor * 0.92, Math.max(vAir, hAir));
        }

        // resolved aim point (closest or body)
        Vec3d resolved = resolveAim(t, eyeY);
        double rx = resolved.x, ry = resolved.y, rz = resolved.z;

        // target / relative motion for lead
        Vec3d tVel = t.getVelocity();
        Vec3d pVel = mc.player.getVelocity();
        double relX = tVel.x - pVel.x, relY = tVel.y - pVel.y, relZ = tVel.z - pVel.z;
        double targetLeadX = tVel.x + relX * 0.08;
        double targetLeadY = tVel.y + relY * 0.10;
        double targetLeadZ = tVel.z + relZ * 0.08;

        if (!aimPointInit) {
            aimX = rx; aimY = ry; aimZ = rz;
            leadX = targetLeadX; leadY = targetLeadY; leadZ = targetLeadZ;
            aimPointInit = true;
        }

        double tHoriz = Math.sqrt(tVel.x * tVel.x + tVel.z * tVel.z);
        double rHoriz = Math.sqrt(relX * relX + relZ * relZ);
        double dist = mc.player.distanceTo(t);

        double aimSmooth = closestPoint.value ? 0.30 : 0.28;
        aimSmooth = MathHelper.clamp(aimSmooth + Math.min(0.35, rHoriz * 0.5), 0.08, 0.75);
        double leadSmooth = MathHelper.clamp(0.18 + Math.min(0.42, (tHoriz + rHoriz * 0.25) * 0.72), 0.12, 0.68);
        double frameScale = MathHelper.clamp(dt * 60.0, 0.45, 2.4);
        double aimBlend = 1.0 - Math.pow(1.0 - aimSmooth, frameScale);
        double leadBlend = 1.0 - Math.pow(1.0 - leadSmooth, frameScale);

        aimX += (rx - aimX) * aimBlend;
        aimY += (ry - aimY) * aimBlend;
        aimZ += (rz - aimZ) * aimBlend;
        leadX += (targetLeadX - leadX) * leadBlend;
        leadY += (targetLeadY - leadY) * leadBlend;
        leadZ += (targetLeadZ - leadZ) * leadBlend;

        // lag clamp
        double lag = Math.sqrt((rx - aimX) * (rx - aimX) + (rz - aimZ) * (rz - aimZ));
        double maxLag = 0.3 + tHoriz * 1.3 + rHoriz * 0.4;
        if (lag > maxLag) {
            aimX = rx; aimY = ry; aimZ = rz;
            leadX = targetLeadX; leadY = targetLeadY; leadZ = targetLeadZ;
        }

        double leadAmt = MathHelper.clamp(0.35 + dist * 0.045 + tHoriz * 0.95 + Math.min(0.18, rHoriz * 0.12), 0.05, 1.05);
        leadAmt *= 0.3 + 0.7 * MathHelper.clamp((dist - 0.8) / 2.5, 0, 1);

        double adjX = aimX + leadX * leadAmt;
        double adjZ = aimZ + leadZ * leadAmt;
        double vertLead = MathHelper.clamp(leadY * Math.min(leadAmt, 0.7 + dist * 0.04)
                * (0.28 + 0.52 * MathHelper.clamp(Math.abs(aimY - eyeY) / 0.7, 0, 1)),
                -(0.16 + dist * 0.055), 0.16 + dist * 0.055);
        double adjY = aimY + vertLead;

        double[] want = angles(eye, new Vec3d(adjX, adjY, adjZ));
        double targetYaw = want[0], targetPitch = want[1];

        // rate-limit target angle changes (anti-snap on teleports)
        if (initialized) {
            double yawChg = Math.abs(MathHelper.wrapDegrees(targetYaw - lastTargetYaw));
            double pitchChg = Math.abs(targetPitch - lastTargetPitch);
            double abrupt = MathHelper.clamp((Math.max(yawChg, pitchChg) - (closestPoint.value ? 20 : 12)) / 50.0, 0, 1);
            overshoot = Math.max(overshoot, abrupt);
            if (abrupt > 0.3) {
                aimStrength *= 0.3;
                aimX = rx; aimY = ry; aimZ = rz;
            }
            double maxYawRate = MathHelper.clamp((120 + rHoriz * 700) * (1 + Math.min(2, yawChg / 60)), 90, 1080);
            double maxPitchRate = MathHelper.clamp((95 + Math.abs(leadY) * 550) * (1 + Math.min(2, pitchChg / 60)), 70, 500);
            double wide = smoothStep(10, 35, Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw())));
            maxYawRate *= 1 + wide * 1.5;
            double yStep = MathHelper.wrapDegrees(targetYaw - lastTargetYaw);
            double pStep = targetPitch - lastTargetPitch;
            yStep = MathHelper.clamp(yStep, -maxYawRate * dt, maxYawRate * dt);
            pStep = MathHelper.clamp(pStep, -maxPitchRate * dt, maxPitchRate * dt);
            targetYaw = lastTargetYaw + yStep;
            targetPitch = lastTargetPitch + pStep;
        }
        overshoot *= Math.pow(0.02, dt);
        if (overshoot < 0.01) overshoot = 0;
        double overshootMul = 1.0 + 3.0 * overshoot;

        double playerYaw = mc.player.getYaw();
        double playerPitch = mc.player.getPitch();
        double errYaw = MathHelper.wrapDegrees(targetYaw - playerYaw);
        double errPitch = vertical.value ? (targetPitch - playerPitch) : 0;

        double absYaw = Math.abs(errYaw), absPitch = Math.abs(errPitch);
        double combined = Math.sqrt(absYaw * absYaw + absPitch * absPitch);

        // aim strength (Vape-style): high when far, soft near crosshair — but never below 0.55
        double desiredStr = 1.0 - smoothStep(0.6, 5.0, combined);
        desiredStr = MathHelper.clamp(desiredStr, 0.75, 1.0);
        if (initialized) {
            double closing = -(absYaw - Math.abs(lastErrYaw)) / dt;
            desiredStr = MathHelper.clamp(desiredStr + MathHelper.clamp(closing / 40.0, -0.15, 0.15), 0.55, 1.0);
        }
        double strBlend = desiredStr > aimStrength
                ? MathHelper.clamp(dt * 12.0, 0.05, 0.55)
                : MathHelper.clamp(dt * 4.0, 0.02, 0.25);
        aimStrength += (desiredStr - aimStrength) * strBlend;
        double str = aimStrength;

        // fade near dead zone
        double fade = smoothStep(deadZone.value, deadZone.value + 2.5, combined);

        // bias
        double hSpeed = horizontalSpeed.value;
        double vSpeed = verticalSpeed.value;
        double hFac = MathHelper.clamp((hSpeed - 10) / 90.0, 0, 1);
        double vFac = MathHelper.clamp((vSpeed - 10) / 90.0, 0, 1);
        if (Math.signum(errYaw) != Math.signum(lastErrYaw) && absYaw > 0.1 && Math.abs(lastErrYaw) > 0.1) yawBias *= 0.3;
        if (Math.signum(errPitch) != Math.signum(lastErrPitch) && absPitch > 0.1 && Math.abs(lastErrPitch) > 0.1) pitchBias *= 0.3;
        double sq = str * str;
        yawBias += errYaw * dt * sq * (1 - hFac);
        pitchBias += errPitch * dt * sq * (1 - vFac);
        double retain = 1.0 - (1.0 - str) * MathHelper.clamp(dt * 5, 0, 0.5);
        yawBias *= retain; pitchBias *= retain;
        yawBias = MathHelper.clamp(yawBias, -15 * (1 - hFac * 0.9), 15 * (1 - hFac * 0.9));
        pitchBias = MathHelper.clamp(pitchBias, -10 * (1 - vFac * 0.9), 10 * (1 - vFac * 0.9));

        // error velocity
        if (initialized) {
            yawVelocity = yawVelocity * 0.85 + ((errYaw - lastErrYaw) / dt) * 0.15;
            pitchVelocity = pitchVelocity * 0.85 + ((errPitch - lastErrPitch) / dt) * 0.15;
        }

        // target angular accel (lead feed-forward)
        if (initialized) {
            double tyRate = MathHelper.wrapDegrees(targetYaw - lastTargetYaw) / dt;
            double tpRate = (targetPitch - lastTargetPitch) / dt;
            double ab = MathHelper.clamp(dt * 12, 0.05, 0.45);
            yawAccel += (tyRate - yawAccel) * ab;
            pitchAccel += (tpRate - pitchAccel) * ab;
        }

        // flick
        double ySign = Math.signum(errYaw);
        if (ySign != 0 && ySign != lastYawSign && absYaw > 0.5) yawFlickTicks = Math.min(yawFlickTicks + 1, 8);
        else yawFlickTicks = Math.max(0, yawFlickTicks - dt * 3);
        lastYawSign = ySign != 0 ? ySign : lastYawSign;
        double pSign = Math.signum(errPitch);
        if (pSign != 0 && pSign != lastPitchSign && absPitch > 0.5) pitchFlickTicks = Math.min(pitchFlickTicks + 1, 8);
        else pitchFlickTicks = Math.max(0, pitchFlickTicks - dt * 3);
        lastPitchSign = pSign != 0 ? pSign : lastPitchSign;
        double yFlick = MathHelper.clamp(yawFlickTicks / 5.0, 0, 1);
        double pFlick = MathHelper.clamp(pitchFlickTicks / 5.0, 0, 1);

        // player own rotation rate (compensate)
        double playerYawRate = 0, playerPitchRate = 0;
        if (initialized) {
            playerYawRate = MathHelper.wrapDegrees(playerYaw - lastPlayerYaw) / dt;
            playerPitchRate = (playerPitch - lastPlayerPitch) / dt;
        }

        // gains from speed sliders (Vape-style)
        double limH = Math.min(hSpeed, 100);
        double limV = Math.min(vSpeed, 100);
        double hGainIn = MathHelper.clamp((limH - 1) / 99.0, 0, 1); hGainIn *= hGainIn;
        double vGainIn = MathHelper.clamp((limV - 1) / 99.0, 0, 1); vGainIn *= vGainIn;
        double hGainScale = 0.20 + 0.80 * hGainIn;
        double vGainScale = 0.20 + 0.80 * vGainIn;

        // Strong base tracking — Strength is the speed slider here
        double strPct = MathHelper.clamp(strength.value / 100.0, 0.0, 1.0);
        // aggressive base gains — scales hard with Strength
        double errGainYaw = lerp(str, 10.0, 7.0 + limH * 0.55) * hGainScale * (1 - 0.25 * yFlick) * (0.85 + 1.4 * strPct);
        double errGainPitch = lerp(str, 8.5, 6.0 + limV * 0.48) * vGainScale * (1 - 0.25 * pFlick) * (0.85 + 1.4 * strPct);
        double biasGainYaw = lerp(str, 0.2, 0.9 + limH * 0.04) * hGainScale;
        double biasGainPitch = lerp(str, 0.15, 0.7 + limV * 0.035) * vGainScale;
        double velGainYaw = lerp(str, 0.08, 0.28) * hGainScale * (1 + 1.8 * yFlick);
        double velGainPitch = lerp(str, 0.06, 0.22) * vGainScale * (1 + 1.8 * pFlick);
        double tgtRateGainYaw = (0.85 + limH * 0.015) * hGainScale;
        double tgtRateGainPitch = (0.82 + limV * 0.013) * vGainScale;
        double playerCompYaw = lerp(str, 0.1, 0.3);
        double playerCompPitch = lerp(str, 0.08, 0.25);

        double yawRate = (errGainYaw * errYaw + biasGainYaw * yawBias + velGainYaw * yawVelocity
                + tgtRateGainYaw * yawAccel - playerCompYaw * playerYawRate) * fade;
        double pitchRate = 0;
        if (vertical.value) {
            pitchRate = (errGainPitch * errPitch + biasGainPitch * pitchBias + velGainPitch * pitchVelocity
                    + tgtRateGainPitch * pitchAccel - playerCompPitch * playerPitchRate) * fade;
            pitchRate += pitchDrift(playerPitch, str, dt, now) * (noise.value / 100.0) * 6.0;
        }

        // strafe increase: if moving sideways away from the target, pull a bit harder
        if (strafeIncrease.value) {
            Vec3d vel = mc.player.getVelocity();
            double lookRad = Math.toRadians(playerYaw);
            // right vector on XZ
            double rightX = -Math.cos(lookRad), rightZ = -Math.sin(lookRad);
            double side = vel.x * rightX + vel.z * rightZ; // positive = strafing right
            if (Math.abs(side) > 0.02) {
                boolean targetRight = errYaw > 0;
                boolean away = (targetRight && side < 0) || (!targetRight && side > 0);
                if (away) yawRate *= 1.15;
            }
        }

        // output limits
        double wideYaw = smoothStep(8, 40, absYaw);
        double wideMul = 1 + wideYaw * 2.5;
        double maxYawOut = Math.min(720 * overshootMul * wideMul,
                Math.max((40 + limH * 0.75) * hGainScale * overshootMul * wideMul,
                         (Math.abs(yawAccel) * 0.55 + 30) * (0.40 + hGainScale * 0.70) * wideMul));
        double maxPitchOut = Math.min(520 * overshootMul,
                Math.max((32 + limV * 0.65) * vGainScale * overshootMul,
                         (Math.abs(pitchAccel) * 0.50 + 24) * (0.40 + vGainScale * 0.70)));
        yawRate = MathHelper.clamp(yawRate, -maxYawOut, maxYawOut);
        pitchRate = MathHelper.clamp(pitchRate, -maxPitchOut, maxPitchOut);

        // distance scale
        double distScale = smoothStep(0.5f, 3.0f, (float) dist);
        yawRate *= lerp(str, 0.15 + 0.5 * wideYaw + (1 - 0.15 - 0.5 * wideYaw) * distScale, 0.4 + 0.6 * distScale);
        pitchRate *= lerp(str, 0.2 + 0.8 * distScale, 0.45 + 0.55 * distScale);

        // multi-band noise
        if (noise.value > 0) {
            double tSec = (now - noiseStartNs) * 1e-9;
            double nStr = (0.15 + 0.85 * str) * (noise.value / 100.0) * (0.5 + 0.5 * (1 - hGainIn));
            double yN = (Math.sin(tSec * 62.83) * 0.4 + Math.sin(tSec * 47.12) * 0.25 + Math.sin(tSec * 78.54) * 0.15
                    + Math.sin(tSec * 12.57) * 0.8 + Math.sin(tSec * 7.85) * 0.5
                    + Math.sin(tSec * 1.26) * 0.3) * nStr * 1.5;
            double pN = (Math.sin(tSec * 56.55 + 1.3) * 0.35 + Math.sin(tSec * 43.98 + 0.7) * 0.2 + Math.sin(tSec * 72.26 + 2.1) * 0.12
                    + Math.sin(tSec * 10.47 + 0.9) * 0.6 + Math.sin(tSec * 5.65 + 1.8) * 0.4
                    + Math.sin(tSec * 0.94 + 0.5) * 0.2) * nStr;
            yawRate += yN;
            if (vertical.value) pitchRate += pN;
        }

        // yield to mouse
        yieldYaw = Math.max(0, yieldYaw - dt);
        yieldPitch = Math.max(0, yieldPitch - dt);
        if (yieldMouse.value && haveApplied) {
            double extY = MathHelper.wrapDegrees(playerYaw - appliedYaw);
            double extP = playerPitch - appliedPitch;
            if (Math.abs(extY) > 0.25 && Math.signum(extY) != Math.signum(errYaw)) yieldYaw = 0.25;
            if (Math.abs(extP) > 0.25 && Math.signum(extP) != Math.signum(errPitch)) yieldPitch = 0.25;
        }
        if (yieldYaw > 0) yawRate *= 0.2;
        if (yieldPitch > 0) pitchRate *= 0.2;

        // smoothing low-pass on the rate
        double tau = 0.008 + 0.12 * smoothing.value / 100.0;
        // (rates are already the output — apply a light blend via integrating with previous applied step feel)
        double stepYaw = yawRate * dt;
        double stepPitch = pitchRate * dt;

        // never hard-overshoot by more than a tiny amount
        if (Math.signum(stepYaw) == Math.signum(errYaw) && Math.abs(stepYaw) > Math.abs(errYaw) + 0.35) {
            stepYaw = errYaw + Math.copySign(0.12, errYaw);
        }
        if (Math.signum(stepPitch) == Math.signum(errPitch) && Math.abs(stepPitch) > Math.abs(errPitch) + 0.35) {
            stepPitch = errPitch + Math.copySign(0.12, errPitch);
        }

        // extra smoothing: blend step toward proportional when smoothing high
        if (smoothing.value > 0) {
            double propY = errYaw * MathHelper.clamp(dt / tau, 0.05, 1.0);
            double propP = errPitch * MathHelper.clamp(dt / tau, 0.05, 1.0);
            double s = smoothing.value / 100.0 * 0.45;
            stepYaw = stepYaw * (1 - s) + propY * s;
            stepPitch = stepPitch * (1 - s) + propP * s;
        }

        lastErrYaw = errYaw;
        lastErrPitch = errPitch;
        lastTargetYaw = targetYaw;
        lastTargetPitch = targetPitch;
        lastPlayerYaw = playerYaw;
        lastPlayerPitch = playerPitch;
        initialized = true;

        // --- Strength: 0 = fully humanized, 100 = blatant lock-on ---
        double blatant = MathHelper.clamp(strength.value / 100.0, 0.0, 1.0);
        double legit = 1.0 - blatant;

        // --- Break Aim Constant / Linear (especially while locked on) ---
        long nowNs = System.nanoTime();
        if (nowNs >= humanNextNs) {
            double lo = 0.25 + 0.30 * blatant;
            double hi = 1.25 + 0.55 * blatant;
            humanMul = lo + Math.random() * (hi - lo);
            pathCurve = ((Math.random() - 0.5) * 0.55) * (0.5 + 0.5 * legit);
            // retarget every 25–90ms
            humanNextNs = nowNs + (25_000_000L + (long) (Math.random() * 65_000_000L));
            // near target: pause more often (humans don't micro-adjust every frame)
            double close = combined < 4.0 ? 0.18 : 0.06;
            if (Math.random() < close * (0.5 + 0.5 * legit)) pauseFrames = 1 + (int) (Math.random() * 2);
        }

        // When almost on target, skip many frames — constant micro-corrections = Aim Constant
        if (combined < deadZone.value + 1.2) {
            if (Math.random() < 0.55 * legit + 0.15) {
                appliedYaw = playerYaw; appliedPitch = playerPitch; haveApplied = true;
                return;
            }
        } else if (combined < 6.0 && Math.random() < 0.22 * legit) {
            appliedYaw = playerYaw; appliedPitch = playerPitch; haveApplied = true;
            return;
        }

        double frameJitter = 0.70 + Math.random() * 0.60; // 0.70..1.30 wider
        double speedMul = humanMul * frameJitter;

        // non-sinusoidal ease with random exponent (breaks Linear)
        double norm = MathHelper.clamp(combined / 30.0, 0.02, 1.0);
        double exp = 0.6 + Math.random() * 1.2;
        double ease = Math.pow(norm, exp);
        ease = 0.25 + 0.75 * ease;
        ease *= 0.65 + Math.random() * 0.70;

        if (pauseFrames > 0) {
            pauseFrames--;
            appliedYaw = playerYaw; appliedPitch = playerPitch; haveApplied = true;
            return;
        }

        if (legit > 0.1 && combined > 10.0 && reactUntilNs < nowNs) {
            reactUntilNs = nowNs + (long) ((0.025 + Math.random() * 0.06) * legit * 1e9);
        }
        if (nowNs < reactUntilNs) {
            stepYaw *= 0.15 + 0.85 * blatant;
            stepPitch *= 0.15 + 0.85 * blatant;
        }

        double strBoost = 0.75 + 1.35 * blatant;
        stepYaw *= speedMul * strBoost * ease;
        stepPitch *= speedMul * strBoost * ease;

        // asymmetric axis + occasional single-axis-only move
        if (Math.random() < 0.40) {
            if (Math.random() < 0.5) stepYaw *= 0.55 + Math.random() * 0.40;
            else stepPitch *= 0.55 + Math.random() * 0.40;
        }
        if (Math.random() < 0.12 * legit) {
            // pure yaw or pure pitch flick — very human, breaks Linear
            if (Math.random() < 0.5) stepPitch *= 0.1;
            else stepYaw *= 0.1;
        }

        // tiny random reverse (overshoot tremor) when close
        if (combined < 8.0 && Math.random() < 0.08 * legit) {
            stepYaw *= -0.15 - Math.random() * 0.2;
            stepPitch *= -0.15 - Math.random() * 0.2;
        }

        // non-linear path arc (legit only)
        double arc = pathCurve * Math.min(1.0, combined / 25.0);
        double arcYaw = -stepPitch * arc * 0.4;
        double arcPitch = stepYaw * arc * 0.25;
        stepYaw += arcYaw;
        stepPitch += arcPitch;

        // accel noise (legit only)
        double accelY = stepYaw - lastStepYaw;
        double accelP = stepPitch - lastStepPitch;
        stepYaw += accelY * (Math.random() * 0.25 * legit);
        stepPitch += accelP * (Math.random() * 0.25 * legit);
        lastStepYaw = stepYaw;
        lastStepPitch = stepPitch;

        // under-aim only on low strength; at 75%+ almost full lock each frame
        double maxFrac = (0.80 + Math.random() * 0.15) * legit + 1.0 * blatant;
        maxFrac = MathHelper.clamp(maxFrac, 0.70, 1.0);
        if (Math.signum(stepYaw) == Math.signum(errYaw) && Math.abs(stepYaw) > Math.abs(errYaw) * maxFrac) {
            stepYaw = errYaw * maxFrac;
        }
        if (Math.signum(stepPitch) == Math.signum(errPitch) && Math.abs(stepPitch) > Math.abs(errPitch) * maxFrac) {
            stepPitch = errPitch * maxFrac;
        }

        // GCD snap always (even blatant looks like mouse). At full blatant we still snap but with fine steps.
        double gcd = sensitivityGcd();
        if (gcd > 1e-6) {
            // blatant: allow sub-GCD by using a fraction so lock stays tight
            double g = gcd * (1.0 - 0.85 * blatant);
            if (g < 1e-6) g = gcd * 0.15;
            stepYaw = Math.round(stepYaw / g) * g;
            stepPitch = Math.round(stepPitch / g) * g;
        }

        if (Math.abs(stepYaw) < 1e-4 && Math.abs(stepPitch) < 1e-4) {
            appliedYaw = playerYaw; appliedPitch = playerPitch; haveApplied = true;
            return;
        }

        mc.player.changeLookDirection(stepYaw / MOUSE_UNIT, stepPitch / MOUSE_UNIT);
        appliedYaw = mc.player.getYaw();
        appliedPitch = mc.player.getPitch();
        haveApplied = true;
    }

    /** Vanilla mouse GCD for the player's current sensitivity (1.21). */
    private double sensitivityGcd() {
        try {
            double sens = mc.options.getMouseSensitivity().getValue(); // 0..1
            double f = sens * 0.6 + 0.2;
            return f * f * f * 1.2 * 0.15;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Vec3d resolveAim(Entity t, double playerEyeY) {
        float pt = Projector.tickProgress;
        Vec3d tp = t.getLerpedPos(pt);
        double minY = tp.y, maxY = tp.y + t.getHeight();
        double h = maxY - minY;
        double centerY = minY + h * (aimHeight.value / 100.0);
        double airOff = Math.min(0.85, airFactor);
        double eyeAligned = MathHelper.clamp(playerEyeY - (0.1 + airOff), minY + 0.01, maxY - 0.01);
        double blend = MathHelper.clamp(airOff / 0.55, 0, 1);
        double y = eyeAligned + (centerY - eyeAligned) * blend;
        y = MathHelper.clamp(y, minY + 0.01, maxY - 0.01);

        if (closestPoint.value && t instanceof LivingEntity) {
            // closest point on AABB to eye
            Vec3d eye = mc.player.getEyePos();
            net.minecraft.util.math.Box box = t.getBoundingBox();
            double cx = MathHelper.clamp(eye.x, box.minX, box.maxX);
            double cy = MathHelper.clamp(eye.y, box.minY, box.maxY);
            double cz = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
            cy = MathHelper.clamp(cy - (0.1 + airOff) + (centerY - cy) * blend, minY + 0.01, maxY - 0.01);
            return new Vec3d(cx, cy, cz);
        }
        return new Vec3d(tp.x, y, tp.z);
    }

    private Vec3d aimPointRaw(Entity e, double playerEyeY) {
        Vec3d p = e.getEntityPos();
        double y = p.y + e.getHeight() * aimHeight.value / 100.0;
        return new Vec3d(p.x, y, p.z);
    }

    private double pitchDrift(double playerPitch, double str, double dt, long now) {
        double strength = 0.65 + 0.35 * str;
        if (driftNextNs == 0 || now >= driftNextNs) {
            double amp = lerp(strength, 0.05, 0.15);
            double corr = playerPitch > 22 ? -0.18 : (playerPitch < -22 ? 0.18 : 0);
            driftTarget = MathHelper.clamp((Math.random() * 2 - 1) * amp + corr, -0.5, 0.5);
            driftNextNs = now + (300 + (long) (Math.random() * 420)) * 1_000_000L;
        }
        double spring = lerp(strength, 2, 5);
        driftVel += (driftTarget - driftPos) * spring * dt;
        driftVel *= Math.pow(0.04, dt);
        driftPos += driftVel * dt;
        double lim = lerp(strength, 0.45, 0.8);
        if (driftPos > lim) { driftPos = lim; driftVel = Math.min(0, driftVel); }
        else if (driftPos < -lim) { driftPos = -lim; driftVel = Math.max(0, driftVel); }
        driftNoise += (Math.random() * 2 - 1 - driftNoise) * MathHelper.clamp(dt * 5, 0.02, 0.18);
        double t = now * 1e-9;
        double osc = Math.sin(t * 8.7 + 0.4) * 0.35 + Math.sin(t * 13.1 + 2.2) * 0.22
                + Math.sin(t * 19.6 + 1.1) * 0.12 + driftNoise * 0.3;
        double out = driftVel * 0.25 - driftPos * lerp(strength, 1.4, 3.0) + osc * lerp(strength, 0.35, 0.9);
        double pScale = 1.0 - 0.85 * smoothStep(72, 88, Math.abs(playerPitch));
        return MathHelper.clamp(out, -lerp(strength, 0.25, 0.55) * pScale, lerp(strength, 0.25, 0.55) * pScale);
    }

    private boolean valid(Entity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity p) return players.value && !p.isSpectator() && !Friends.shield(p);
        return e instanceof LivingEntity && mobs.value;
    }

    private static double smoothStep(double e0, double e1, double x) {
        double n = MathHelper.clamp((x - e0) / Math.max(1e-6, e1 - e0), 0, 1);
        return n * n * (3 - 2 * n);
    }

    private static double lerp(double t, double a, double b) { return a + t * (b - a); }

    private static double[] angles(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new double[]{yaw, pitch};
    }
}

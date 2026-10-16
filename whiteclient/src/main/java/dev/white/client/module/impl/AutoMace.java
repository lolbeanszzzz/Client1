package dev.white.client.module.impl;

import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.BlockPlacer;
import dev.white.client.util.CombatControl;
import dev.white.client.util.CombatReach;
import dev.white.client.util.Friends;
import dev.white.client.util.Rotations;
import dev.white.client.util.SwapLimit;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * AutoMace — dedicated smash module inspired by Vape's AutoMace.
 *
 * Features:
 *  - Auto Density / Breach mace selection from fall height
 *  - Aim (silent or visible) at nearest valid target while falling
 *  - Attack through AttackPackets
 *  - Full stun-slam sequence: axe (shield break) → short wait → mace smash
 *  - Auto unequip Elytra for smash, optional re-equip after bounce
 *
 * Uses Rotations, AttackPackets, CombatControl, SwapLimit / BlockPlacer.
 * Cooperates with AutoWeapon when it is enabled, but can run the full sequence alone.
 */
public class AutoMace extends Module {

    private enum Phase {
        IDLE,
        AXE_SWAP,       // switching to axe
        AXE_HIT,        // about to / just sent axe hit
        WAIT_SHIELD,    // waiting for shield to drop after axe
        MACE_SWAP,      // switching to mace
        MACE_HIT,       // about to / just sent mace hit
        DONE
    }

    // ---- settings ----
    private final Setting.Bool playersOnly = add(new Setting.Bool("Players only", true));
    private final Setting.Bool autoSelect = add(new Setting.Bool("Auto mace select", true));
    private final Setting.Bool preferDensity = add(new Setting.Bool("Prefer Density (else Breach)", true));
    private final Setting.Num densityFrom = add(new Setting.Num("Density from fall (blocks)", 3.0, 1.5, 12.0, 0.1));
    private final Setting.Bool anyMace = add(new Setting.Bool("Use any mace if none", true));

    private final Setting.Bool stunSlam = add(new Setting.Bool("Stun slam (axe then mace)", true));
    private final Setting.Num stunChance = add(new Setting.Num("Stun slam chance %", 100, 0, 100, 1));
    private final Setting.Num shieldWait = add(new Setting.Num("Shield wait (ticks)", 3, 1, 12, 1));

    private final Setting.Bool aim = add(new Setting.Bool("Aim", true));
    private final Setting.Bool silentAim = add(new Setting.Bool("Silent aim", true));
    private final Setting.Num aimRange = add(new Setting.Num("Aim range", 5.0, 2.0, 10.0, 0.1));
    private final Setting.Num aimSpeed = add(new Setting.Num("Aim speed (deg/tick)", 18, 3, 60, 1));
    private final Setting.Num aimHeight = add(new Setting.Num("Aim height %", 60, 10, 100, 5));

    private final Setting.Bool attack = add(new Setting.Bool("Attack", true));
    private final Setting.Num minFall = add(new Setting.Num("Min fall (blocks)", 1.5, 1.5, 8.0, 0.1));
    private final Setting.Bool smashOnly = add(new Setting.Bool("Smash only (no grounded hits)", true));

    private final Setting.Bool autoUnequipElytra = add(new Setting.Bool("Auto unequip Elytra", true));
    private final Setting.Bool reEquipElytra = add(new Setting.Bool("Re-equip Elytra", true));
    private final Setting.Num reEquipDelay = add(new Setting.Num("Re-equip delay (ticks)", 6, 1, 30, 1));
    private final Setting.Bool swapBack = add(new Setting.Bool("Swap back after hit", true));
    private final Setting.Num swapBackDelay = add(new Setting.Num("Swap back delay (ticks)", 4, 0, 20, 1));

    private final Setting.Bool requireWeapon = add(new Setting.Bool("Only while holding weapon", false));
    private final Setting.Bool chatDebug = add(new Setting.Bool("Debug chat", false));

    // ---- state ----
    private Entity target;
    private float silentYaw, silentPitch;
    private boolean aiming;

    private int originalHotbar = -1;
    private int chestplateHotbar = -1;
    private boolean elytraUnequipped;
    private boolean waitingReequip;
    private int reequipAt = -1;
    private boolean smashUsedThisFall;
    private double spentY;
    private int hitCooldown;
    private int restoreAt = -1;          // client age when we restore original hotbar

    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int stunTargetId = -1;
    private boolean stunRolledThisFall;   // only roll stun chance once per fall

    private static final int CHEST_HANDLER_SLOT = 6;

    public AutoMace() {
        super("Auto Mace", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        resetSequence();
    }

    @Override
    protected void onDisable() {
        Rotations.release(this);
        CombatControl.release(this);
        if (elytraUnequipped && reEquipElytra.value) tryReequipElytra(true);
        if (originalHotbar >= 0 && mc.player != null) BlockPlacer.selectForce(originalHotbar);
        resetSequence();
    }

    private void resetSequence() {
        target = null;
        aiming = false;
        originalHotbar = -1;
        chestplateHotbar = -1;
        elytraUnequipped = false;
        waitingReequip = false;
        reequipAt = -1;
        smashUsedThisFall = false;
        spentY = 0;
        hitCooldown = 0;
        restoreAt = -1;
        silentYaw = silentPitch = 0;
        phase = Phase.IDLE;
        phaseTicks = 0;
        stunTargetId = -1;
        stunRolledThisFall = false;
    }

    @Override
    public boolean early() {
        return true;
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void onPreTick() {
        CombatControl.tick();
        if (hitCooldown > 0) hitCooldown--;
        if (phaseTicks > 0) phaseTicks--;

        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            releaseAim();
            return;
        }
        if (mc.currentScreen != null || Freecam.active() || mc.player.isSpectator()) {
            releaseAim();
            return;
        }

        // Grounded: clear fall + phase, maybe re-equip
        if (mc.player.isOnGround()) {
            smashUsedThisFall = false;
            stunRolledThisFall = false;
            phase = Phase.IDLE;
            phaseTicks = 0;
            stunTargetId = -1;
            if (waitingReequip && mc.player.age >= reequipAt) tryReequipElytra(false);
            releaseAim();
            CombatControl.release(this);
            if (originalHotbar >= 0 && hitCooldown <= 0) {
                BlockPlacer.select(originalHotbar);
                originalHotbar = -1;
            }
            return;
        }

        double fall = effectiveFall();
        boolean canSmashNow = !mc.player.isGliding() && fall > minFall.value;

        // Aim early even before smash height
        if (aim.value && fall > 0.4) {
            target = pickTarget();
            if (target != null) updateAim(target);
            else releaseAim();
        } else if (!canSmashNow) {
            releaseAim();
        }

        if (!canSmashNow) return;

        // Exclusive claim while in a smash window
        if (!CombatControl.claim(this, CombatControl.PRIORITY_AUTOMACE, 10)) {
            if (aim.value && target != null) updateAim(target);
            return;
        }
        CombatControl.renew(this, 8);

        // Unequip elytra so smash is legal
        if (autoUnequipElytra.value && mc.player.isGliding()) {
            releaseAim();
            return;
        }
        if (autoUnequipElytra.value && isWearingElytra() && !elytraUnequipped) {
            if (tryUnequipElytra()) dbg("unequipped elytra for smash");
        }

        if (target == null) target = pickTarget();
        if (target == null) {
            releaseAim();
            phase = Phase.IDLE;
            return;
        }

        if (aim.value) updateAim(target);
        else releaseAim();

        // Restore original hotbar after a completed sequence (not only when grounded)
        if (restoreAt >= 0 && mc.player.age >= restoreAt && originalHotbar >= 0) {
            BlockPlacer.select(originalHotbar);
            dbg("swap back → slot " + originalHotbar);
            originalHotbar = -1;
            restoreAt = -1;
        }

        if (!attack.value) return;
        if (smashOnly.value && mc.player.isOnGround()) return;
        if (requireWeapon.value && !isWeapon(mc.player.getMainHandStack()) && phase == Phase.IDLE) return;

        // Drive the phase machine even during hitCooldown so stun wait/swap progresses
        runPhase();
    }

    // -------------------------------------------------------------------------
    // Stun-slam / smash phase machine
    // -------------------------------------------------------------------------

    private void runPhase() {
        switch (phase) {
            case IDLE -> {
                boolean wantStun = shouldStunSlam(target);
                if (wantStun) {
                    int axe = findAxeSlot();
                    int mace = pickMaceSlot();
                    if (axe >= 0 && mace >= 0) {
                        phase = Phase.AXE_SWAP;
                        phaseTicks = 1;
                        stunTargetId = target.getId();
                        dbg("stun-slam start → axe slot " + axe + " then mace slot " + mace);
                        ensureSlot(axe);
                        return;
                    }
                    dbg("stun wanted but missing axe/mace in hotbar");
                }
                // plain smash
                ensureMaceInHand();
                if (!holdingMace()) return;
                if (hitCooldown > 0) return;
                doHit("mace smash");
                markSmash();
                phase = Phase.DONE;
                scheduleReequip();
            }
            case AXE_SWAP -> {
                int axe = findAxeSlot();
                if (axe < 0) { phase = Phase.IDLE; return; }
                if (BlockPlacer.selected() != axe) {
                    ensureSlot(axe);
                    return;
                }
                // axe in hand — hit next tick if ready
                if (phaseTicks > 0) return;
                phase = Phase.AXE_HIT;
            }
            case AXE_HIT -> {
                if (!holdingAxe()) {
                    phase = Phase.AXE_SWAP;
                    phaseTicks = 1;
                    return;
                }
                if (target == null || target.getId() != stunTargetId || !target.isAlive()) {
                    phase = Phase.IDLE;
                    return;
                }
                if (hitCooldown > 0) return;
                if (!CombatReach.inReach(target)) return;
                doHit("stun axe");
                phase = Phase.WAIT_SHIELD;
                phaseTicks = Math.max(1, shieldWait.intValue());
                dbg("waiting " + phaseTicks + " ticks for shield");
            }
            case WAIT_SHIELD -> {
                if (phaseTicks > 0) {
                    // keep aiming
                    return;
                }
                phase = Phase.MACE_SWAP;
                phaseTicks = 2;
                int mace = pickMaceSlot();
                if (mace >= 0) ensureSlot(mace);
            }
            case MACE_SWAP -> {
                int mace = pickMaceSlot();
                if (mace < 0) { phase = Phase.IDLE; return; }
                if (BlockPlacer.selected() != mace) {
                    ensureSlot(mace);
                    return;
                }
                if (phaseTicks > 0) return;
                phase = Phase.MACE_HIT;
            }
            case MACE_HIT -> {
                if (!holdingMace()) {
                    phase = Phase.MACE_SWAP;
                    phaseTicks = 1;
                    return;
                }
                if (target == null || !target.isAlive()) {
                    phase = Phase.IDLE;
                    return;
                }
                if (hitCooldown > 0) return;
                if (!CombatReach.inReach(target)) return;
                doHit("stun mace");
                markSmash();
                phase = Phase.DONE;
                scheduleReequip();
            }
            case DONE -> {
                CombatControl.renew(this, 4);
                // after swap-back fires, go idle so another fall can re-arm
                if (restoreAt < 0 || (originalHotbar < 0 && mc.player.age >= restoreAt)) {
                    phase = Phase.IDLE;
                    stunTargetId = -1;
                }
            }
        }
    }

    /**
     * Stun when: setting on, target is a player who is blocking (or would block a hit),
     * and we have both an axe and a mace in the hotbar. Chance is rolled once per fall.
     */
    private boolean shouldStunSlam(Entity e) {
        if (!stunSlam.value) return false;
        if (!(e instanceof PlayerEntity p)) return false;
        if (findAxeSlot() < 0 || pickMaceSlot() < 0) return false;
        boolean blocking = p.isBlocking() || AutoWeapon.wouldBlockHit(p);
        if (!blocking) return false;
        if (!stunRolledThisFall) {
            stunRolledThisFall = true;
            boolean ok = Math.random() * 100.0 < stunChance.value;
            if (!ok) dbg("stun-slam rolled miss");
            return ok;
        }
        // already committed this fall — keep going even if shield animation lagged
        return phase != Phase.IDLE || blocking;
    }

    private void doHit(String label) {
        if (target == null) return;
        // Only gate the actual swing on cooldown; state machine keeps running
        if (hitCooldown > 0) return;
        if (!CombatReach.inReach(target)) {
            dbg(label + " skipped (out of reach)");
            return;
        }
        if (silentAim.value && Rotations.ownedBy(this)) {
            Rotations.sendLookPacket(true);
        }
        final int id = target.getId();
        AttackPackets.hit(target, e -> e.isAlive() && e.getId() == id && CombatReach.inReach(e));
        hitCooldown = 2; // short; stun wait uses phaseTicks instead
        dbg(label + " → " + target.getName().getString());
    }

    private void scheduleReequip() {
        if (reEquipElytra.value && elytraUnequipped) {
            waitingReequip = true;
            reequipAt = mc.player.age + reEquipDelay.intValue();
        }
        if (swapBack.value && originalHotbar >= 0) {
            restoreAt = mc.player.age + swapBackDelay.intValue();
        }
    }

    // -------------------------------------------------------------------------
    // Targeting & aim
    // -------------------------------------------------------------------------

    private Entity pickTarget() {
        Entity best = null;
        double bestD = aimRange.value;
        double rangeSq = bestD * bestD;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) continue;
            if (e == mc.player) continue;
            if (Friends.shield(e)) continue;
            if (playersOnly.value && !(e instanceof PlayerEntity)) continue;
            if (e instanceof PlayerEntity p && p.isSpectator()) continue;

            double d = mc.player.squaredDistanceTo(e);
            if (d > rangeSq) continue;

            double dy = e.getY() - mc.player.getY();
            if (dy > 1.5) continue;

            if (best == null || d < bestD * bestD) {
                best = e;
                bestD = Math.sqrt(d);
            }
        }
        return best;
    }

    private void updateAim(Entity e) {
        float[] ang = anglesTo(aimPoint(e));
        float wantYaw = ang[0];
        float wantPitch = ang[1];

        if (silentAim.value) {
            if (!aiming) {
                silentYaw = mc.player.getYaw();
                silentPitch = mc.player.getPitch();
            }
            float step = (float) aimSpeed.value;
            silentYaw = approachAngle(silentYaw, wantYaw, step);
            silentPitch = MathHelper.clamp(approachAngle(silentPitch, wantPitch, step), -90f, 90f);
            if (Rotations.set(this, silentYaw, silentPitch)) aiming = true;
            else aiming = false;
        } else {
            float step = (float) aimSpeed.value;
            mc.player.setYaw(approachAngle(mc.player.getYaw(), wantYaw, step));
            mc.player.setPitch(MathHelper.clamp(approachAngle(mc.player.getPitch(), wantPitch, step), -90f, 90f));
            aiming = true;
            Rotations.release(this);
        }
    }

    private void releaseAim() {
        if (aiming) {
            Rotations.release(this);
            aiming = false;
        }
    }

    private Vec3d aimPoint(Entity e) {
        var b = e.getBoundingBox();
        double h = aimHeight.value / 100.0;
        return new Vec3d((b.minX + b.maxX) * 0.5, b.minY + (b.maxY - b.minY) * h, (b.minZ + b.maxZ) * 0.5);
    }

    private float[] anglesTo(Vec3d point) {
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{ yaw, pitch };
    }

    private static float approachAngle(float current, float target, float maxStep) {
        float diff = MathHelper.wrapDegrees(target - current);
        if (diff > maxStep) diff = maxStep;
        if (diff < -maxStep) diff = -maxStep;
        return current + diff;
    }

    // -------------------------------------------------------------------------
    // Slots
    // -------------------------------------------------------------------------

    private void ensureMaceInHand() {
        // Prefer AutoWeapon when it is managing mace
        if (AutoWeapon.willMaceSmash() || AutoWeapon.wantsStep(target)) {
            AutoWeapon.onTarget(target);
            return;
        }
        int slot = pickMaceSlot();
        if (slot >= 0) ensureSlot(slot);
    }

    private void ensureSlot(int slot) {
        if (slot < 0 || mc.player == null) return;
        int cur = BlockPlacer.selected();
        if (slot == cur) return;
        if (originalHotbar < 0) originalHotbar = cur;
        // Mid stun-slam we must get the slot; force if the soft limit already spent this tick
        if (!BlockPlacer.select(slot) && phase != Phase.IDLE) {
            BlockPlacer.selectForce(slot);
        }
    }

    private int pickMaceSlot() {
        boolean deep = effectiveFall() >= densityFrom.value;
        RegistryKey<Enchantment> want;
        if (autoSelect.value) want = deep ? Enchantments.DENSITY : Enchantments.BREACH;
        else want = preferDensity.value ? Enchantments.DENSITY : Enchantments.BREACH;

        int slot = BlockPlacer.findSlot(s -> isMace(s) && enchantLevel(s, want) > 0);
        if (slot < 0 && anyMace.value) slot = BlockPlacer.findSlot(AutoMace::isMace);
        return slot;
    }

    private int findAxeSlot() {
        return BlockPlacer.findSlot(s -> !s.isEmpty() && s.isIn(ItemTags.AXES));
    }

    private boolean holdingMace() {
        return mc.player != null && isMace(mc.player.getMainHandStack());
    }

    private boolean holdingAxe() {
        ItemStack s = mc.player.getMainHandStack();
        return !s.isEmpty() && s.isIn(ItemTags.AXES);
    }

    // -------------------------------------------------------------------------
    // Fall / smash
    // -------------------------------------------------------------------------

    private double effectiveFall() {
        if (mc.player == null) return 0;
        if (smashUsedThisFall) return Math.max(0, spentY - mc.player.getY());
        return mc.player.fallDistance;
    }

    private void markSmash() {
        smashUsedThisFall = true;
        spentY = mc.player.getY();
    }

    // -------------------------------------------------------------------------
    // Elytra
    // -------------------------------------------------------------------------

    private boolean isWearingElytra() {
        return mc.player != null && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private boolean tryUnequipElytra() {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return false;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;
        if (!SwapLimit.take()) return false;

        int chestSlot = BlockPlacer.findSlot(AutoMace::isChestplate);
        int emptyHotbar = BlockPlacer.findSlot(ItemStack::isEmpty);
        ItemStack worn = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!worn.isOf(Items.ELYTRA)) return false;

        int hotbar = chestSlot >= 0 ? chestSlot : emptyHotbar;
        if (hotbar < 0) return false;

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, CHEST_HANDLER_SLOT, hotbar, SlotActionType.SWAP, mc.player);
        chestplateHotbar = hotbar;
        elytraUnequipped = true;
        return true;
    }

    private void tryReequipElytra(boolean force) {
        if (!elytraUnequipped || mc.player == null || mc.interactionManager == null) return;
        if (!force && mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        if (!force && !SwapLimit.take()) return;

        int elytraHotbar = -1;
        if (chestplateHotbar >= 0) {
            ItemStack s = mc.player.getInventory().getStack(chestplateHotbar);
            if (s.isOf(Items.ELYTRA)) elytraHotbar = chestplateHotbar;
        }
        if (elytraHotbar < 0) elytraHotbar = BlockPlacer.findSlot(s -> s.isOf(Items.ELYTRA));
        if (elytraHotbar < 0) {
            elytraUnequipped = false;
            waitingReequip = false;
            return;
        }
        if (force) SwapLimit.take();
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, CHEST_HANDLER_SLOT, elytraHotbar, SlotActionType.SWAP, mc.player);
        elytraUnequipped = false;
        waitingReequip = false;
        chestplateHotbar = -1;
        dbg("re-equipped elytra");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isMace(ItemStack s) { return s.isOf(Items.MACE); }

    private static boolean isWeapon(ItemStack s) {
        return isMace(s) || s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES) || s.isOf(Items.TRIDENT);
    }

    private static boolean isChestplate(ItemStack s) {
        if (s.isEmpty()) return false;
        return s.getItem().toString().toLowerCase().contains("chestplate");
    }

    private static int enchantLevel(ItemStack s, RegistryKey<Enchantment> key) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.matchesKey(key)) return s.getEnchantments().getLevel(e);
        }
        return 0;
    }

    private void dbg(String msg) {
        if (chatDebug.value && mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("§7[AutoMace] §f" + msg), false);
        }
    }
}

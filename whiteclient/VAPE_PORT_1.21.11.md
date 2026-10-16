# Whiteclient — Vape behaviour port (Fabric, 1.21.11 only)

This is a **real Fabric reimplementation** of selected Vape features for **Minecraft 1.21.x**, not a dump of Vape’s binaries or libraries.

## Scope (current)

| Feature | Status |
|--------|--------|
| Click GUI (Vape-style, no logo) | Ported look & interaction on Whiteclient GUI |
| Trigger Bot | Ported decision flow from Vape Triggerbot |

## Not in this build yet

Aim Assist, Silent Aura, AutoMace, PearlCatch, Sprint, Keep Sprint — next phases.

## Trigger Bot (Vape parity notes)

Decision order matches Vape:

1. `canAttack` (screen / freecam / use-item / mouse-down / weapons / air crits)
2. Crosshair entity valid
3. Select first hit
4. Mouse-over delay (random in range)
5. Attack ready with extra-delay offset (negative = early)
6. Optional miss chance
7. Hit via AttackPackets (+ AutoWeapon slot help)

## How to use

- Right Shift → Click GUI (disable Sidebar UI / Blob UI if those are on)
- Enable **Trigger Bot** under Combat
- Rebuild the mod as usual for your 1.21.11 Fabric setup

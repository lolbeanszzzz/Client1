package dev.white.client.util;

import net.minecraft.client.option.KeyBinding;

import java.util.HashSet;
import java.util.Set;

/** Keys that modules want the game to treat as held down (read by KeyBindingMixin). */
public final class ForcedKeys {
    private static final Set<KeyBinding> KEYS = new HashSet<>();
    private ForcedKeys() {}

    public static void force(KeyBinding k) { KEYS.add(k); }
    public static void release(KeyBinding k) { KEYS.remove(k); }
    public static boolean isForced(KeyBinding k) { return KEYS.contains(k); }
}

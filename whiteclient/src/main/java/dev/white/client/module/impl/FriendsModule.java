package dev.white.client.module.impl;

import dev.white.client.command.CommandManager;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.Friends;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;

/**
 * Friends manager. While this module is ON, Kill Aura and Trigger Bot never target a friend, and Hitboxes /
 * Tracers draw them in the friend colour. The same list is editable with .friend add/remove/list.
 */
public class FriendsModule extends Module {
    private final Setting.Hue color = add(new Setting.Hue("Friend color", 0.40f).wheel(0.7f, 1f));
    private final Setting.Num range = add(new Setting.Num("Add-nearby range", 8, 2, 32, 1));

    public FriendsModule() {
        super("Friends", Category.MISC);
        // click a box, type the username, press Enter (Ctrl+V works too) - the player doesn't need to be nearby
        add(new Setting.Text("Type name to add", FriendsModule::addTyped));
        add(new Setting.Text("Type name to remove", FriendsModule::removeTyped));
        add(new Setting.Button("Add aimed player", () -> {
            PlayerEntity p = aimed();
            if (p == null) { CommandManager.say("Look at a player first"); return; }
            String n = p.getName().getString();
            CommandManager.say(Friends.add(n) ? "Added " + n + " as a friend" : n + " is already a friend");
        }));
        add(new Setting.Button("Remove aimed player", () -> {
            PlayerEntity p = aimed();
            if (p == null) { CommandManager.say("Look at a player first"); return; }
            String n = p.getName().getString();
            CommandManager.say(Friends.remove(n) ? "Removed " + n : n + " wasn't a friend");
        }));
        add(new Setting.Button("Add nearby players", () -> {
            if (mc.world == null || mc.player == null) return;
            int added = 0;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || mc.player.distanceTo(p) > range.value) continue;
                if (Friends.add(p.getName().getString())) added++;
            }
            CommandManager.say("Added " + added + " nearby player" + (added == 1 ? "" : "s"));
        }));
        add(new Setting.Button("Show friends", () -> {
            var l = Friends.list();
            CommandManager.say(l.isEmpty() ? "No friends yet." : "Friends (" + l.size() + "): " + String.join(", ", l));
        }));
        add(new Setting.Button("Clear friends", () -> { Friends.clear(); CommandManager.say("Friends list cleared"); }));
        setEnabled(true);                       // friend protection is on by default
        Friends.protectSource = this::isEnabled;
        Friends.colorSource = () -> 0xFF000000 | color.rgb();
    }

    /** Cleans up what was typed: drops a leading @ and rejects anything that cannot be a username. */
    private static String clean(String raw) {
        String n = raw.trim();
        if (n.startsWith("@")) n = n.substring(1);
        if (n.isEmpty() || n.length() > 24 || !n.matches("[A-Za-z0-9_.\\-]+")) {
            CommandManager.say("\"" + raw + "\" isn't a valid username");
            return null;
        }
        return n;
    }

    private static void addTyped(String raw) {
        String n = clean(raw);
        if (n != null) CommandManager.say(Friends.add(n) ? "Added " + n + " as a friend" : n + " is already a friend");
    }

    private static void removeTyped(String raw) {
        String n = clean(raw);
        if (n != null) CommandManager.say(Friends.remove(n) ? "Removed " + n : n + " wasn't a friend");
    }

    private static PlayerEntity aimed() {
        return mc.crosshairTarget instanceof EntityHitResult h && h.getEntity() instanceof PlayerEntity p && p != mc.player ? p : null;
    }
}

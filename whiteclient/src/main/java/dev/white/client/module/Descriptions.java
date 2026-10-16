package dev.white.client.module;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** One-line descriptions shown under the module names in the sidebar menu. Modules without an entry just show their name. */
public final class Descriptions {
    private Descriptions() {}
    private static final Map<String, String> MAP = new HashMap<>();
    private static void d(String name, String text) { MAP.put(name.toLowerCase(Locale.ROOT), text); }

    static {
        // Render
        d("HUD", "Menu colour, rounding, blur, watermark and module list");
        d("Sidebar UI", "Switches between the classic windows and this sidebar menu");
        d("Blob UI", "Glowing blob hub menu with a glass panel per category");
        d("Hitboxes", "Filled 3D hitbox boxes around players, with a custom colour, through walls");
        d("Tracers", "Lines from your crosshair to every loaded player");
        d("Player Info", "Name, ping, health and armor above players");
        d("Fullbright", "Full brightness everywhere");
        d("Radial Menu", "A radial menu with three slices; click a slice to send its text or command");
        d("Auto Tool", "Picks the fastest hotbar tool for the block you are mining");
        d("Auto Fish", "Casts, reels in on a bite and casts again while you hold a fishing rod");
        d("Username Hider", "Shows a fake name instead of your username on your screen");
        d("Freelook", "Look around while your character keeps facing forward");
        d("Waypoints", "Saved coordinates drawn on screen with distance");
        d("Add Waypoint", "Saves a waypoint at your feet when its key is pressed");
        d("Snowflakes", "Snow falls down the screen while the menu is open");
        d("Sky", "White or gray sky, clouds and matching fog");
        d("Spotify", "Shows the song Spotify is playing");
        // Combat
        d("Silent Aura", "Kill aura that turns only what the server sees");
        d("Trigger Bot", "Attacks when the crosshair is on a player");
        d("Auto Clicker", "Clicks for you at a set rate while you hold attack on a target");
        d("Kill Aura", "Attacks the nearest player in range");
        d("Aim Assist", "Smoothly turns toward the target nearest your crosshair");
        d("Breach Swap", "Hits with a Breach mace from the hotbar, then swaps back");
        d("Auto Weapon", "Picks the best weapon for the target: axe on shields, mace when falling, stun slam");
        d("Spear Swap", "Selects a hotbar spear for the hit, then switches back");
        d("Auto Box", "Walls in the nearest player with blocks");
        d("Auto Cobweb", "Places a cobweb on the nearest player");
        d("Surround", "Places blocks on the four sides of your feet");
        d("Totem Pops", "Chat line and counter on every totem pop");
        d("Auto Totem", "Keeps a Totem of Undying in your offhand");
        d("Auto Armor", "Wears the best armor you carry");
        d("Auto Shield Break", "Axes a blocking player's shield, then switches back");
        d("Fast Crystal", "No crystal place delay and instant crystal breaking");
        d("Auto Dtap", "Crystal double tap with optional auto break");
        d("Criticals", "Small in-air packets so ground hits crit");
        d("Velocity", "Scales knockback down");
        d("Jump Reset", "Jumps the moment you are hit to cut knockback");
        d("Hitbox Expand", "Bigger hitboxes on other players for your aim, so you can hit off-centre");
        d("Reach", "Extends block and entity interaction range, with an optional hit chance");
        // Movement
        d("Flight", "Fly with WASD, jump and sneak");
        d("Bhop", "Auto-jump while moving");
        d("Scaffold", "Places blocks under your feet as you walk");
        d("Elytra Fly", "Elytra flight controls");
        d("Entity Speed", "Sets the speed of the mount you are riding");
        d("Auto Walk", "Holds the forward key for you");
        d("No Slow", "No walking slowdown while eating or blocking");
        d("No Fall", "Cancels fall damage");
        d("Safe Walk", "Stops you walking off drops bigger than the limit");
        d("Freeze", "Server sees you standing still while you move");
        d("Baritone", "Controls for the Baritone pathfinding mod");
        // Player
        d("Freecam", "Fly the camera without moving your character");
        d("Auto Eat", "Eats the best food when hunger gets low");
        d("Pearl Catch", "Throws a pearl, then a wind charge that meets it in the air");
        d("Auto MLG", "Places water under you before a long fall");
        d("Auto Tunnel", "Digs a straight tunnel ahead of you");
        d("Auto Replenish", "Refills a hotbar slot when its stack runs out");
        d("Chest Stealer", "Shift-clicks everything out of an open container");
        d("Inv Sorter", "Sorts your inventory, biggest stacks first");
        d("Elytra Swap", "Wears the hotbar Elytra with the most durability");
        d("Ding Dong Builder", "Builds a small structure from hotbar blocks");
        d("Toggle All", "Master switch: everything off, then back on");
        d("Macro", "Plays back your recorded macro");
        d("Macro Record", "Records your keys, clicks and camera turns");
        // Base Finding
        d("Block ESP", "Draws chosen blocks through walls");
        d("Sus Chunk Finder", "Rates and highlights chunks that look built in");
        d("Stash Finder", "Prints coordinates where many chests sit together");
        d("New Chunks", "Shows chunks generated since the server last saw them");
        d("Trail Tracker", "Follows long thin trails of new chunks");
        d("Logout Spots", "Marks where players logged out");
        d("Geode ESP", "Finds geodes with many grown clusters");
        d("Ancient Debris", "Outlines ancient debris through walls (only what the server sends; anti-xray hides buried ore)");
        d("Found List", "Everything the finders have found, in one list");
        // Misc
        d("Friends", "Players protected from your combat modules");
        d("Config", "Save, load, export and import your settings");
    }

    public static String of(Module m) { return MAP.getOrDefault(m.name.toLowerCase(Locale.ROOT), ""); }
}

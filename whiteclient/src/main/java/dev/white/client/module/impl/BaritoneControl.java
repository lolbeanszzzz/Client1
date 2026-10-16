package dev.white.client.module.impl;

import dev.white.client.WhiteClient;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.StringJoiner;

/**
 * Buttons for the Baritone pathfinding mod. Baritone is a separate mod: put its jar in your mods folder
 * (for 1.21.11 that is baritone-standalone-fabric, release v1.17.0 from github.com/cabaletta/baritone/releases).
 * This module only sends Baritone's own "#" chat commands for you, after checking that Baritone is loaded, so
 * nothing is ever sent to the server as chat. Bind a key to the module itself: pressing it stops whatever
 * Baritone is doing (#cancel).
 */
public class BaritoneControl extends Module {
    private static final String[] MOD_IDS = {"baritone", "baritone-meteor"};

    private final Setting.Num thisWay = add(new Setting.Num("This way (blocks)", 100, 10, 1000, 10));
    private final Setting.Num tunnelH = add(new Setting.Num("Tunnel height", 2, 1, 5, 1));
    private final Setting.Num tunnelW = add(new Setting.Num("Tunnel width", 1, 1, 5, 1));
    private final Setting.Num tunnelD = add(new Setting.Num("Tunnel length (0 = forever)", 0, 0, 500, 10));
    private final Setting.Num digY = add(new Setting.Num("Dig-to Y level", 12, -64, 320, 1));
    private final Setting.Bool allowBreak = add(new Setting.Bool("Allow break", true));
    private final Setting.Bool allowPlace = add(new Setting.Bool("Allow place", true));
    private final Setting.Bool allowParkour = add(new Setting.Bool("Allow parkour", true));
    private final Setting.Bool sprint = add(new Setting.Bool("Allow sprint", true));

    public BaritoneControl() {
        super("Baritone", Category.MOVEMENT);
        add(new Setting.Button("Stop (cancel)", () -> cmd("cancel")));
        add(new Setting.Button("Pause", () -> cmd("pause")));
        add(new Setting.Button("Resume", () -> cmd("resume")));
        add(new Setting.Button("Go this way", () -> cmd("thisway " + thisWay.intValue())));
        add(new Setting.Button("Tunnel ahead", this::tunnel));
        add(new Setting.Button("Dig to Y level", () -> cmd("goto " + digY.intValue())));
        add(new Setting.Button("Apply move settings", this::applySettings));
        add(new Setting.Button("Surface", () -> cmd("surface")));
        add(new Setting.Button("Come to me", () -> cmd("come")));
        add(new Setting.Button("Nearest found base", this::gotoBase));
        add(new Setting.Button("Nearest trail head", this::gotoTrail));
        add(new Setting.Button("Nearest debris", this::gotoDebris));
        add(new Setting.Button("Follow nearest", this::followNearest));
        add(new Setting.Button("Blacklist closest", () -> cmd("blacklist")));
        add(new Setting.Button("Invert goal", () -> cmd("invert")));
        add(new Setting.Button("ETA / progress", () -> cmd("eta")));
        add(new Setting.Button("Nearest waypoint", this::gotoWaypoint));
        add(new Setting.Button("Mine ESP blocks", this::mineEspBlocks));
        add(new Setting.Button("Explore", () -> cmd("explore")));
        add(new Setting.Button("Farm", () -> cmd("farm")));
        add(new Setting.Button("Follow players", () -> cmd("follow players")));
    }

    public static boolean installed() {
        for (String id : MOD_IDS) if (FabricLoader.getInstance().isModLoaded(id)) return true;
        return false;
    }

    private void say(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Baritone] " + s), false);
    }

    /** Runs a Baritone command, e.g. cmd("goto 1 64 2") sends "#goto 1 64 2" which Baritone handles client-side. */
    public void cmd(String command) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        if (!installed()) {
            say("Baritone is not installed. Put baritone-standalone-fabric (v1.17.0 for 1.21.11) in your mods folder.");
            return;
        }
        mc.player.networkHandler.sendChatMessage("#" + command);
    }

    /**
     * Sends ordinary chat without Baritone grabbing it. With Baritone's "chatControl" on (its default), any chat message
     * that happens to be a Baritone command word ("come", "stop", "mine", "help", "goto 1 2 3"...) is run by Baritone and
     * never reaches the server. This switches chatControl / chatControlAnyway off for the single send, then puts them back.
     * Messages that start with Baritone's own prefix (default "#") are left alone: those are meant for Baritone.
     */
    public static void sendPlainChat(net.minecraft.client.network.ClientPlayNetworkHandler net, String msg) {
        java.util.List<Object[]> restore = new java.util.ArrayList<>();   // {setting object, value field, old value}
        try {
            if (installed()) {
                Object settings = Class.forName("baritone.api.BaritoneAPI").getMethod("getSettings").invoke(null);
                String prefix = "#";
                try {
                    Object ps = settings.getClass().getField("prefix").get(settings);
                    prefix = String.valueOf(ps.getClass().getField("value").get(ps));
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
                if (prefix.isEmpty() || !msg.startsWith(prefix)) {
                    for (String name : new String[]{"chatControl", "chatControlAnyway"}) {
                        try {
                            Object setting = settings.getClass().getField(name).get(settings);
                            java.lang.reflect.Field value = setting.getClass().getField("value");
                            Object old = value.get(setting);
                            value.set(setting, Boolean.FALSE);
                            restore.add(new Object[]{setting, value, old});
                        } catch (ReflectiveOperationException | RuntimeException ignored) {}
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        try {
            net.sendChatMessage(msg);
        } finally {
            for (Object[] r : restore) {
                try { ((java.lang.reflect.Field) r[1]).set(r[0], r[2]); } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
    }

    /** #tunnel <height> <width> <depth>; depth 0 digs until stopped. */
    private void tunnel() {
        int d = tunnelD.intValue();
        cmd("tunnel " + tunnelH.intValue() + " " + tunnelW.intValue() + (d > 0 ? " " + d : ""));
    }

    private void applySettings() {
        cmd("set allowBreak " + allowBreak.value);
        cmd("set allowPlace " + allowPlace.value);
        cmd("set allowParkour " + allowParkour.value);
        cmd("set allowSprint " + sprint.value);
        say("Movement settings sent to Baritone.");
    }

    private void gotoBase() {
        if (mc.player == null) return;
        dev.white.client.util.Finds.Find best = null;
        double bd = Double.MAX_VALUE;
        for (var f : dev.white.client.util.Finds.all()) {
            if (f.source().equals("Debris")) continue;       // debris has its own button
            double d = Math.hypot(f.x() + 0.5 - mc.player.getX(), f.z() + 0.5 - mc.player.getZ());
            if (d < bd) { bd = d; best = f; }
        }
        if (best == null) { say("Nothing in the found list yet."); return; }
        say("Going to " + best.source() + ": " + best.label());
        cmd("goto " + best.x() + " " + best.y() + " " + best.z());
    }

    private void gotoTrail() {
        if (mc.player == null) return;
        dev.white.client.util.Finds.Find best = null;
        double bd = Double.MAX_VALUE;
        for (var f : dev.white.client.util.Finds.all()) {
            if (!f.source().equals("Trail")) continue;
            double d = Math.hypot(f.x() + 0.5 - mc.player.getX(), f.z() + 0.5 - mc.player.getZ());
            if (d < bd) { bd = d; best = f; }
        }
        if (best == null) { say("No trails found (turn on New Chunks and Trail Tracker)."); return; }
        cmd("goto " + best.x() + " " + best.z());
    }

    private void gotoDebris() {
        AncientDebris ad = null;
        for (Module m : WhiteClient.modules.modules) if (m instanceof AncientDebris a) ad = a;
        net.minecraft.util.math.BlockPos b = ad == null || !ad.isEnabled() ? null : ad.nearest();
        if (b == null) { say("No ancient debris found (turn on Ancient Debris and get close to some)."); return; }
        say("Going to ancient debris at " + b.getX() + " " + b.getY() + " " + b.getZ());
        cmd("goto " + b.getX() + " " + b.getY() + " " + b.getZ());
    }

    private void followNearest() {
        if (mc.player == null || mc.world == null) return;
        net.minecraft.entity.player.PlayerEntity best = null;
        double bd = Double.MAX_VALUE;
        for (var p : mc.world.getPlayers()) {
            if (p == mc.player || dev.white.client.util.Friends.is(p)) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < bd) { bd = d; best = p; }
        }
        if (best == null) { say("No other player nearby."); return; }
        cmd("follow player " + best.getName().getString());
    }

    private void gotoWaypoint() {
        if (mc.player == null) return;
        Waypoints w = Waypoints.get();
        Waypoints.Wp best = null;
        double bd = Double.MAX_VALUE;
        if (w != null) for (Waypoints.Wp p : w.here()) {
            double d = mc.player.squaredDistanceTo(p.x + 0.5, p.y, p.z + 0.5);
            if (d < bd) { bd = d; best = p; }
        }
        if (best == null) { say("No waypoints in this world."); return; }
        say("Going to " + best.name);
        cmd("goto " + best.x + " " + best.y + " " + best.z);
    }

    private void mineEspBlocks() {
        BlockEsp esp = null;
        for (Module m : WhiteClient.modules.modules) if (m instanceof BlockEsp b) esp = b;
        if (esp == null || esp.targets.isEmpty()) { say("Choose blocks in Block ESP first."); return; }
        StringJoiner ids = new StringJoiner(" ");
        for (Block b : esp.targets) ids.add(Registries.BLOCK.getId(b).toString());
        cmd("mine " + ids);
    }

    /** The module key = panic button: stop pathing. */
    @Override public void toggle() { cmd("cancel"); }
    @Override public void setEnabled(boolean value) { if (value) cmd("cancel"); }
}

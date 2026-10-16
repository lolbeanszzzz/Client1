package dev.white.client.module.impl;

import dev.white.client.command.CommandManager;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.Gfx;
import dev.white.client.util.Look;
import dev.white.client.util.Projector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers where players were when they logged out. A player that disappears is only counted as a logout if they
 * are also gone from the server's player list a couple of seconds later (so walking out of range doesn't count).
 * Each spot is drawn as a ghost box with the name and how long ago it was.
 */
public class LogoutSpots extends Module {
    private static LogoutSpots instance;

    private record Seen(String name, double x, double y, double z) {}
    private record Pending(Seen seen, int since) {}
    private static final class Spot {
        final String name, key;
        final double x, y, z;
        final long time;
        Spot(String name, String key, double x, double y, double z, long time) {
            this.name = name; this.key = key; this.x = x; this.y = y; this.z = z; this.time = time;
        }
    }

    private final Setting.Num keepMinutes = add(new Setting.Num("Keep for (min)", 30, 1, 240, 1));
    private final Setting.Num maxSpots = add(new Setting.Num("Max spots", 20, 1, 100, 1));
    private final Setting.Num maxDistance = add(new Setting.Num("Max distance (0=any)", 0, 0, 3000, 100));
    private final Setting.Bool chatMessage = add(new Setting.Bool("Chat message", true));
    private final Setting.Bool ghostBox = add(new Setting.Bool("Ghost box", true));
    private final Setting.Hue color = add(new Setting.Hue("Color", 0.08f).wheel());

    private final Map<UUID, Seen> seen = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final List<Spot> spots = new ArrayList<>();
    private String lastKey = "";
    private int tick;
    private final double[] o = new double[2];

    public LogoutSpots() {
        super("Logout Spots", Category.BASE);
        instance = this;
        add(new Setting.Button("Clear spots", this::clear));
    }

    public static LogoutSpots get() { return instance; }

    private String worldKey() {
        String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "singleplayer";
        String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "?";
        return server + "|" + dim;
    }

    public void clear() {
        spots.clear();
        CommandManager.say("Logout spots cleared.");
    }

    public void listInChat() {
        String k = worldKey();
        int n = 0;
        long now = System.currentTimeMillis();
        for (Spot s : spots) {
            if (!s.key.equals(k)) continue;
            CommandManager.say(s.name + ": " + (int) s.x + " " + (int) s.y + " " + (int) s.z + "  (" + ago(now - s.time) + " ago)");
            n++;
        }
        if (n == 0) CommandManager.say("No logout spots in this world.");
    }

    @Override
    protected void onDisable() { seen.clear(); pending.clear(); }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;
        String k = worldKey();
        if (!k.equals(lastKey)) { lastKey = k; seen.clear(); pending.clear(); }
        tick++;

        Set<UUID> now = new HashSet<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            UUID id = p.getUuid();
            now.add(id);
            seen.put(id, new Seen(p.getName().getString(), p.getX(), p.getY(), p.getZ()));
            pending.remove(id);
        }

        for (Map.Entry<UUID, Seen> e : seen.entrySet())            // anyone who just vanished from view
            if (!now.contains(e.getKey()) && !pending.containsKey(e.getKey())) pending.put(e.getKey(), new Pending(e.getValue(), tick));

        Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            if (now.contains(e.getKey())) { it.remove(); continue; }
            if (tick - e.getValue().since() < 40) continue;          // wait ~2 s for the player list to update
            it.remove();
            seen.remove(e.getKey());
            if (mc.getNetworkHandler().getPlayerListEntry(e.getKey()) == null) record(e.getValue().seen());
        }
        seen.keySet().removeIf(id -> !now.contains(id) && !pending.containsKey(id));
    }

    private void record(Seen s) {
        spots.add(new Spot(s.name(), worldKey(), s.x(), s.y(), s.z(), System.currentTimeMillis()));
        int limit = maxSpots.intValue();
        while (spots.size() > limit) spots.remove(0);
        if (chatMessage.value) CommandManager.say(s.name() + " logged out at " + (int) s.x() + " " + (int) s.y() + " " + (int) s.z());
    }

    private static String ago(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "s";
        long m = s / 60;
        return m < 60 ? m + "m" : (m / 60) + "h " + (m % 60) + "m";
    }

    @Override
    public void onHud(DrawContext g) {
        if (spots.isEmpty()) return;
        long now = System.currentTimeMillis(), maxMs = keepMinutes.intValue() * 60_000L;
        spots.removeIf(s -> now - s.time > maxMs);
        String k = worldKey();
        var tr = mc.textRenderer;
        int rgb = color.rgb(), col = 0xFF000000 | rgb, shade = ghostBox.value ? Look.withAlpha(rgb, 38) : 0;
        double md = maxDistance.value;

        for (Spot s : spots) {
            if (!s.key.equals(k)) continue;
            double dx = s.x - Projector.camPos.x, dy = s.y - Projector.camPos.y, dz = s.z - Projector.camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (md > 0 && dist > md) continue;
            if (ghostBox.value) Gfx.cube(g, s.x - 0.3, s.y, s.z - 0.3, s.x + 0.3, s.y + 1.8, s.z + 0.3, 1, col, shade, true);
            if (!Projector.project(s.x, s.y + 2.1, s.z, o)) continue;
            String text = s.name + "  " + ago(now - s.time) + " ago  " + Math.round(dist) + "m";
            Look.tagClamped(g, tr, text, (int) o[0], (int) o[1] - 14, col);
        }
    }
}

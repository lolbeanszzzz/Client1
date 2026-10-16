package dev.white.client.module.impl;

import dev.white.client.util.SdfText;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.Friends;
import dev.white.client.util.Look;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Counts Totem of Undying pops. The server tells every client when a player's totem pops (entity status 35) and when
 * an entity dies (status 3); ClientPlayNetworkHandlerMixin forwards both here. Shows a chat line on every pop, an
 * optional sound, and a small on-screen list of recent pops with their count. The count resets when that player dies.
 */
public class TotemPops extends Module {
    private static final byte STATUS_TOTEM = 35, STATUS_DEATH = 3;
    private static final SoundEvent ALERT = SoundEvent.of(Identifier.of("whiteclient", "alert"));
    private static TotemPops instance;

    private final Setting.Bool chat = add(new Setting.Bool("Chat message", true));
    private final Setting.Bool sound = add(new Setting.Bool("Alert sound", false));
    private final Setting.Bool hudList = add(new Setting.Bool("HUD list", true));
    private final Setting.Num showFor = add(new Setting.Num("List time (s)", 60, 10, 600, 10));
    private final Setting.Bool self = add(new Setting.Bool("Count yourself", false));
    private final Setting.Bool skipFriends = add(new Setting.Bool("Ignore friends", false));
    private final Setting.Bool deathMsg = add(new Setting.Bool("Death message", true));

    private static final class Pops {
        String name;
        int count;
        long last;
    }

    private final Map<UUID, Pops> pops = new HashMap<>();
    private World lastWorld;

    public TotemPops() {
        super("Totem Pops", Category.COMBAT);
        instance = this;
    }

    @Override protected void onDisable() { pops.clear(); }

    @Override
    public void onTick() {
        if (mc.world != lastWorld) { lastWorld = mc.world; pops.clear(); }
    }

    /** Called from ClientPlayNetworkHandlerMixin for every entity status packet (game thread). */
    public static void onStatus(Entity e, byte status) {
        if (instance == null || !instance.isEnabled()) return;
        if (status != STATUS_TOTEM && status != STATUS_DEATH) return;
        if (e instanceof PlayerEntity p) instance.handle(p, status);
    }

    private void handle(PlayerEntity p, byte status) {
        if (mc.player == null) return;
        if (p == mc.player && !self.value) return;
        if (skipFriends.value && Friends.is(p)) return;
        String name = p.getName().getString();
        if (status == STATUS_TOTEM) {
            Pops e = pops.computeIfAbsent(p.getUuid(), k -> new Pops());
            e.name = name;
            e.count++;
            e.last = System.currentTimeMillis();
            if (chat.value) {
                mc.player.sendMessage(Text.literal("[Pops] ").formatted(Formatting.GOLD)
                        .append(Text.literal(name + " popped a totem (" + e.count + ")").formatted(Formatting.YELLOW)), false);
            }
            if (sound.value) mc.player.playSound(ALERT, 1.0f, 1.0f);
        } else {
            Pops e = pops.remove(p.getUuid());
            if (e != null && deathMsg.value) {
                mc.player.sendMessage(Text.literal("[Pops] ").formatted(Formatting.GOLD)
                        .append(Text.literal(name + " died after " + e.count + " pop" + (e.count == 1 ? "" : "s")).formatted(Formatting.RED)), false);
            }
        }
    }

    @Override
    public void onHud(DrawContext g) {
        if (!hudList.value || pops.isEmpty()) return;
        long now = System.currentTimeMillis(), keep = (long) showFor.value * 1000L;
        pops.values().removeIf(p -> now - p.last > keep);
        if (pops.isEmpty()) return;
        List<Pops> list = new ArrayList<>(pops.values());
        list.sort((a, b) -> b.count != a.count ? Integer.compare(b.count, a.count) : Long.compare(b.last, a.last));
        if (list.size() > 8) list = list.subList(0, 8);

        TextRenderer tr = mc.textRenderer;
        int w = SdfText.w("Totem pops") + 16;
        for (Pops p : list) w = Math.max(w, SdfText.w(p.name) + SdfText.w("x" + p.count) + 28);
        int x = 4, y = 38, h = 14 + list.size() * 11;
        Look.panel(g, x, y, x + w, y + h, Look.WARN);
        SdfText.ui(g, "Totem pops", x + 8, y + 3, Look.MUTED, false);
        int ry = y + 14;
        for (Pops p : list) {
            SdfText.ui(g, p.name, x + 8, ry, Look.TEXT, false);
            String c = "x" + p.count;
            SdfText.ui(g, c, x + w - 7 - SdfText.w(c), ry, p.count >= 3 ? Look.BAD : Look.WARN, false);
            ry += 11;
        }
    }
}

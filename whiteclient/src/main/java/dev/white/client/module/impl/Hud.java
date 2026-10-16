package dev.white.client.module.impl;

import dev.white.client.util.SdfText;
import dev.white.client.WhiteClient;
import dev.white.client.gui.Theme;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.Projector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Colour and look of the whole client. Color/Saturation recolour every menu AND every in-world overlay (Sus Chunk
 * Finder, ESP tags, ...); Corner radius, Menu opacity and Menu blur style the windows. Switching the module on also
 * shows an on-screen watermark and enabled-module list. Saturation 0 = the default white look.
 */
public class Hud extends Module {
    public static final String NAME = "ilIegal client";
    public static final String VERSION = "1.1";
    private static Hud instance;

    // One colour for everything: the menus, every window and the in-world overlays (Sus Chunk Finder, ESP tags, ...)
    private final Setting.Hue color = add(new Setting.Hue("Color", 0.6f));
    private final Setting.Num saturation = add(new Setting.Num("Saturation", 0.0, 0.0, 1.0, 0.05));
    private final Setting.Num menuOpacity = add(new Setting.Num("Menu opacity %", 92, 60, 100, 2));
    private final Setting.Num overlayOpacity = add(new Setting.Num("Overlay opacity %", 82, 30, 100, 2));
    private final Setting.Num corners = add(new Setting.Num("Corner radius", 4, 0, 8, 1));
    private final Setting.Bool blur = add(new Setting.Bool("Menu blur", true));
    /** Shader look: glow halo around windows (0 = off) and the organic blob edge instead of a plain rounded rectangle. */
    private final Setting.Num glow = add(new Setting.Num("Glow %", 60, 0, 100, 5));
    private final Setting.Bool blobShape = add(new Setting.Bool("Blob shape", true));
    private final Setting.Bool accentOnly = add(new Setting.Bool("One accent colour", false));
    private final Setting.Bool plates = add(new Setting.Bool("Glass plates", true));   // shader plates behind the watermark and module list
    private final Setting.Bool watermark = add(new Setting.Bool("Watermark", true));
    private final Setting.Bool list = add(new Setting.Bool("Module list", true));
    /** Switches view bobbing + sprint/speed FOV change off while ESP-style overlays are on, so boxes never slide off their block. */
    private final Setting.Bool steady = add(new Setting.Bool("Steady view (ESP)", true));

    public Hud() {
        super("HUD", Category.RENDER);
        instance = this;
        applyTheme();
    }

    /** Picked menu colour (0..1 hue) and tint strength, used by the sidebar menu for its accent. */
    public static float hue() { return instance == null ? 0.6f : instance.color.hue; }
    public static float saturation() { return instance == null ? 0f : (float) instance.saturation.value; }

    public static boolean steadyView() { return instance == null || instance.steady.value; }

    /** Palette button in the menu: next accent colour (turns the tint on if the menu was still white / grey). */
    public static void cycleAccent() {
        if (instance == null) return;
        if (instance.saturation.value < 0.2) instance.saturation.value = 0.6;
        instance.color.hue = (instance.color.hue + 0.1f) % 1f;
        applyTheme();
    }

    /** Palette button, right click: back to the plain white / grey look. */
    public static void resetAccent() {
        if (instance == null) return;
        instance.saturation.value = 0.0;
        applyTheme();
    }

    /** True when the menus should blur the world behind them (uses the game's own menu blur, see Options > Accessibility). */
    public static boolean menuBlur() { return instance == null || instance.blur.value; }

    /** Pushes the chosen colour, corner radius and opacity into the menu theme and the overlays. Cheap; called whenever a menu screen draws. */
    public static void applyTheme() {
        if (instance == null) return;
        float hue = instance.color.hue, sat = (float) instance.saturation.value;
        dev.white.client.util.Ui.radius = instance.corners.intValue();
        dev.white.client.util.ShaderUi.glow = (float) instance.glow.value / 100f;
        dev.white.client.util.ShaderUi.blob = instance.blobShape.value;
        Theme.apply(hue, sat, (float) instance.menuOpacity.value / 100f);
        // the in-world overlays follow the same colour; saturation is also how strongly their plates are tinted
        dev.white.client.util.Look.apply(hue, sat, (float) instance.overlayOpacity.value / 100f, instance.accentOnly.value);
    }

    private int rgb() {
        return 0xFF000000 | MathHelper.hsvToRgb(color.hue, (float) saturation.value, 1f);
    }

    @Override
    public void onHud(DrawContext g) {
        applyTheme();
        int c = rgb();
        var tr = mc.textRenderer;
        int r = Math.min(dev.white.client.util.Ui.radius, 5);
        if (watermark.value) {
            String wm = NAME + " " + VERSION;
            if (plates.value) dev.white.client.util.Ui.panel(g, 2, 2, 6 + SdfText.w(wm) + 4, 15, r, dev.white.client.util.Look.BG, dev.white.client.util.Look.EDGE, c & 0x00FFFFFF | 0x60000000, false);
            SdfText.ui(g, wm, plates.value ? 6 : 4, plates.value ? 5 : 4, c, true);
        }
        if (!list.value) return;

        List<String> names = new ArrayList<>();
        for (Module m : WhiteClient.modules.modules) if (m.isEnabled() && m != this) names.add(m.name);
        names.sort(Comparator.comparingInt((String n) -> SdfText.w(n)).reversed());
        int y = plates.value ? 20 : 4;
        for (String n : names) {
            int w = SdfText.w(n), x2 = Projector.width() - 2;
            if (plates.value) {
                dev.white.client.util.Ui.panel(g, x2 - w - 8, y - 1, x2, y + 10, r, dev.white.client.util.Look.BG, dev.white.client.util.Look.EDGE, 0, false);
                dev.white.client.util.Ui.corners(g, x2 - 3, y + 1, x2 - 1, y + 8, 1, c, true, true, true, true);   // accent tick on the right edge
            }
            SdfText.ui(g, n, x2 - w - (plates.value ? 6 : 2), y, c, true);
            y += plates.value ? 13 : 10;
        }
    }
}

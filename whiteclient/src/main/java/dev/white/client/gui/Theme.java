package dev.white.client.gui;

import net.minecraft.util.math.MathHelper;

/**
 * All GUI colours (ARGB) in one place. Everything here is driven by the HUD module's Color / Saturation /
 * Menu opacity settings (saturation 0 = the classic white/grey look), and Look uses the same hue for the world overlays.
 */
public final class Theme {
    private Theme() {}
    public static int TITLE       = 0xFFFFFFFF;
    public static int TITLE_TEXT  = 0xFF1E1E1E;
    public static int BORDER      = 0xFF9A9A9A;
    public static int ROW         = 0xF0F4F4F4;
    public static int ROW_HOVER   = 0xF0E4E4E4;
    public static int TEXT        = 0xFF303030;
    public static int ON          = 0xFF2B2B2B;
    public static int ON_TEXT     = 0xFFFFFFFF;
    public static int SETTING     = 0xF0EAEAEA;
    public static int SLIDER_BG   = 0xFFD2D2D2;
    public static int SLIDER      = 0xFF9C9C9C;
    /** Light (frosted white) or dark (smoked) menu. Toggled by the sun / moon switch in the menu. */
    public static boolean dark;
    public static int PANEL      = 0xC8F4F5F7;   // translucent window body
    public static int PANEL_EDGE = 0x80FFFFFF;   // thin soft edge around windows
    public static int HOVER      = 0x22000000;   // row hover pill
    public static int MUTED      = 0xFF8A8F99;
    public static int GLOW       = 0x38000000;   // halo colour behind windows (alpha is scaled by HUD -> Glow)

    /** Recolours the whole menu. opacity (0.6..1) is how solid the rows are, lower lets the blurred world show through. */
    public static void apply(float hue, float saturation, float opacity) {
        float s = MathHelper.clamp(saturation, 0f, 1f);
        int a = MathHelper.clamp(Math.round(opacity * 255), 150, 255);
        if (!dark) {
            int light = 0xFF000000 | MathHelper.hsvToRgb(hue, s, 1f);
            int accent = 0xFF000000 | MathHelper.hsvToRgb(hue, s, 0.17f + 0.43f * s);
            TITLE = light;
            TITLE_TEXT = 0xFF23262D;
            TEXT = 0xFF2A2D34;
            ON = accent;
            ON_TEXT = luminance(accent) > 140 ? 0xFF1E1E1E : 0xFFFFFFFF;
            ROW       = shade(hue, 0.10f * s, 0.957f, a);
            ROW_HOVER = shade(hue, 0.18f * s, 0.894f, a);
            SETTING   = shade(hue, 0.10f * s, 0.90f, Math.min(a, 0xB4));
            BORDER    = shade(hue, 0.45f * s, 0.604f, 255);
            SLIDER_BG = shade(hue, 0.15f * s, 0.80f, 255);
            SLIDER    = shade(hue, 0.50f * s, 0.62f, 255);
            PANEL      = shade(hue, 0.06f * s, 0.965f, Math.round(Math.min(a, 0xC8) * 0.45f));   // mostly see-through
            PANEL_EDGE = 0xB0FFFFFF;
            HOVER      = 0x1F000000;
            MUTED      = 0xFF8A8F99;
            GLOW       = (0x40 << 24) | (MathHelper.hsvToRgb(hue, 0.6f * s, 0.35f) & 0xFFFFFF);
        } else {
            int accent = 0xFF000000 | MathHelper.hsvToRgb(hue, 0.55f * s, 0.93f);
            TITLE = shade(hue, 0.30f * s, 0.16f, 255);
            TITLE_TEXT = 0xFFF1F3F8;
            TEXT = 0xFFE6E9F0;
            ON = accent;
            ON_TEXT = 0xFF14161B;
            ROW       = shade(hue, 0.30f * s, 0.14f, a);
            ROW_HOVER = shade(hue, 0.30f * s, 0.22f, a);
            SETTING   = shade(hue, 0.30f * s, 0.20f, Math.min(a, 0xC8));
            BORDER    = shade(hue, 0.30f * s, 0.36f, 255);
            SLIDER_BG = shade(hue, 0.30f * s, 0.30f, 255);
            SLIDER    = shade(hue, 0.55f * s, 0.62f, 255);
            PANEL      = shade(hue, 0.35f * s, 0.16f, Math.round(Math.min(a, 0xC8) * 0.38f));   // mostly see-through
            PANEL_EDGE = 0xA0FFFFFF;
            HOVER      = 0x22FFFFFF;
            MUTED      = 0xFF8E95A3;
            GLOW       = (0x90 << 24) | (MathHelper.hsvToRgb(hue, 0.45f * s, 1f) & 0xFFFFFF);
        }
    }

    private static int shade(float hue, float s, float v, int alpha) {
        return (alpha << 24) | (MathHelper.hsvToRgb(hue, s, v) & 0xFFFFFF);
    }

    private static int luminance(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }
}

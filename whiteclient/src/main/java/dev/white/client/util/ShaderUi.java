package dev.white.client.util;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * GPU shapes for every menu and overlay. Instead of building a rounded rectangle out of dozens of 1-pixel-row
 * g.fill() calls, a shape is ONE textured quad drawn with one of our own fragment shaders
 * (assets/whiteclient/shaders/core/ui_*.fsh) that evaluates a rounded-box signed distance field per pixel:
 * perfectly smooth corners at any GUI scale, soft glow, frosted gradient, inner rim light, optional organic "blob" edge.
 *
 * How parameters reach the shader without custom uniforms (the GUI renderer owns the render pass):
 *  - the quad's UVs run -1..1, so the shader knows where inside the shape it is,
 *  - the shape size / pixel size are recovered from screen-space derivatives of the GUI position,
 *  - colour is the vertex colour; the low 2 bits of r,g,b carry a 6-bit parameter (corner radius, or glow spread + radius).
 * The quad goes through DrawContext, so scissors, matrix scaling and draw order with text / icons all keep working.
 */
public final class ShaderUi {
    private ShaderUi() {}

    public enum Mode { FLAT, PANEL, RING, GLOW, RAMP_UP, RAMP_DOWN, HUE, SLICE, SLICE_RING, SEG, SEG_RING, TEXT, LINE, BLOBX, BLOBX_RING, WHEEL }

    private static final String NS = "whiteclient";
    private static final Identifier DUMMY = Identifier.of(NS, "textures/gui/icons/crown.png");   // any texture: the shader never reads it

    private static final RenderPipeline[] SOLID = new RenderPipeline[Mode.values().length];
    private static final RenderPipeline[] BLOB = new RenderPipeline[Mode.values().length];

    static {
        for (Mode m : Mode.values()) {
            String n = m.name().toLowerCase();
            SOLID[m.ordinal()] = build(n);
            if (m == Mode.PANEL || m == Mode.RING || m == Mode.GLOW) BLOB[m.ordinal()] = build(n + "_blob");
        }
    }

    private static RenderPipeline build(String shader) {
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                .withLocation(Identifier.of(NS, "pipeline/ui_" + shader))
                .withVertexShader(Identifier.of(NS, "core/ui_shape"))
                .withFragmentShader(Identifier.of(NS, "core/ui_" + shader))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .build());
    }

    /** Call once from the mod initializer so the pipelines are registered before the first frame. */
    public static void init() {}

    /** Organic blob edge instead of a plain rounded rectangle (HUD -> Blob shape). Set by the HUD module. */
    public static boolean blob = true;
    /** Strength of the glow halo, 0..1 (HUD -> Glow). Set by the HUD module. */
    public static float glow = 0.6f;

    /** Colour with a 6-bit parameter hidden in the low 2 bits of r, g and b (the shader reads it back). */
    static int pack(int argb, int param) {
        param = Math.max(0, Math.min(63, param));
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r = (r & 0xFC) | ((param >> 4) & 3);
        g = (g & 0xFC) | ((param >> 2) & 3);
        b = (b & 0xFC) | (param & 3);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void quad(DrawContext g, RenderPipeline pipe, int x1, int y1, int x2, int y2, int packed) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0 || pipe == null) return;
        // u,v = -1, region 2, texture size 1  ->  UVs run -1..1 across the quad
        g.drawTexture(pipe, DUMMY, x1, y1, -1f, -1f, w, h, 2, 2, 1, 1, packed);
    }

    /** A shape filling the box with the given radius (GUI px). Alpha 0 draws nothing. */
    public static void shape(DrawContext g, Mode mode, int x1, int y1, int x2, int y2, int radius, int argb, boolean blobShape) {
        if ((argb >>> 24) == 0) return;
        RenderPipeline p = blobShape && BLOB[mode.ordinal()] != null ? BLOB[mode.ordinal()] : SOLID[mode.ordinal()];
        quad(g, p, x1, y1, x2, y2, pack(argb, radius));
    }

    public static void shape(DrawContext g, Mode mode, int x1, int y1, int x2, int y2, int radius, int argb) {
        shape(g, mode, x1, y1, x2, y2, radius, argb, false);
    }

    /** Soft halo around a box (drawn behind it; nothing is drawn under the box itself). spread / radius are rounded to even numbers, max 14. */
    public static void halo(DrawContext g, int x1, int y1, int x2, int y2, int radius, int spread, int argb, boolean blobShape) {
        if ((argb >>> 24) == 0 || spread < 2) return;
        spread = Math.min(14, spread) & ~1;
        radius = Math.min(14, radius) & ~1;
        RenderPipeline p = blobShape && BLOB[Mode.GLOW.ordinal()] != null ? BLOB[Mode.GLOW.ordinal()] : SOLID[Mode.GLOW.ordinal()];
        quad(g, p, x1 - spread, y1 - spread, x2 + spread, y2 + spread, pack(argb, ((spread / 2) << 3) | (radius / 2)));
    }

    /**
     * One slice of the radial menu (a 112-degree ring segment, rounded corners) as a single shader quad.
     * index 0 = top, 1 = lower right, 2 = lower left; (cx, cy) is the circle centre (already moved by the hover offset),
     * rOut / rIn are the ring radii in GUI px. ring = true draws the glowing edge instead of the body.
     */
    public static void slice(DrawContext g, boolean ring, int index, float cx, float cy, float rOut, float rIn, int argb) {
        if ((argb >>> 24) == 0) return;
        int ro = Math.max(2, Math.round(rOut)), r = ro + 8;       // 8 px of room around the ring for the glow
        int ratio = Math.max(0, Math.min(15, Math.round(rIn / ro * 40f)));
        int x1 = Math.round(cx) - r, y1 = Math.round(cy) - r;
        quad(g, SOLID[(ring ? Mode.SLICE_RING : Mode.SLICE).ordinal()], x1, y1, x1 + 2 * r, y1 + 2 * r, pack(argb, (ratio << 2) | index));
    }

    /**
     * One tab of the Blob UI hub ring: segment `index` of `n` (0 = top, going clockwise), a soft blobby ring piece with a
     * gap to its neighbours, as a single shader quad. (cx, cy) is the ring centre (already moved by the hover offset),
     * rOut the outer radius in GUI px (the inner radius is 56% of it). ring = true draws the glowing edge instead of the body.
     */
    public static void segment(DrawContext g, boolean ring, int index, int n, float cx, float cy, float rOut, int argb) {
        if ((argb >>> 24) == 0) return;
        int ro = Math.max(4, Math.round(rOut)), r = ro + 10;      // 10 px of room around the ring for the glow (matches the shader)
        int x1 = Math.round(cx) - r, y1 = Math.round(cy) - r;
        int code = ((Math.max(2, Math.min(9, n)) - 2) << 3) | (index & 7);
        quad(g, SOLID[(ring ? Mode.SEG_RING : Mode.SEG).ordinal()], x1, y1, x1 + 2 * r, y1 + 2 * r, pack(argb, code));
    }

    /** The shader text pipeline (SDF glyph atlas), used by SdfText. */
    public static RenderPipeline textPipeline() { return SOLID[Mode.TEXT.ordinal()]; }

    /**
     * Anti-aliased line with round caps and a faint glow, drawn as one rotated shader quad.
     * width = line width in GUI px. Coordinates are in the current matrix space.
     */
    public static void line(DrawContext g, double x1, double y1, double x2, double y2, float width, int argb) {
        if ((argb >>> 24) == 0) return;
        double dx = x2 - x1, dy = y2 - y1, len = Math.hypot(dx, dy);
        if (!(len > 0.5) || len > 20000) return;
        float hw = Math.max(0.5f, width / 2f);
        int code = Math.max(1, Math.min(63, Math.round(hw * 4f)));
        int L = 2 * (int) Math.ceil(len / 2.0) + 8;                  // even, with 4 px of room per end for the round caps + glow
        int H = 2 * (int) Math.ceil(hw + 4f);
        var m = g.getMatrices();
        m.pushMatrix();
        m.translate((float) ((x1 + x2) / 2.0), (float) ((y1 + y2) / 2.0));
        m.rotate((float) Math.atan2(dy, dx));
        quad(g, SOLID[Mode.LINE.ordinal()], -L / 2, -H / 2, L / 2, H / 2, pack(argb, code));
        m.popMatrix();
    }

    /**
     * Organic blob (the reference menu look): body = frosted translucent fill, edge = glowing white outline with a wide halo.
     * (cx, cy) is the centre, hw / hh the half size of the area it may use (the shape fills ~75-90% of it), seed 0..63 picks the lobes.
     */
    public static void blob(DrawContext g, int cx, int cy, int hw, int hh, int seed, int fill, int edge) {
        quad(g, SOLID[Mode.BLOBX.ordinal()], cx - hw, cy - hh, cx + hw, cy + hh, pack(fill, seed));
        quad(g, SOLID[Mode.BLOBX_RING.ordinal()], cx - hw, cy - hh, cx + hw, cy + hh, pack(edge, seed));
    }
}

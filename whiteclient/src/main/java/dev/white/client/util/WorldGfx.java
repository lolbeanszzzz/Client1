package dev.white.client.util;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * World-space ESP renderer, the same idea Meteor Client uses (Renderer3D):
 *
 *  - boxes are queued as real world coordinates while the world is being rendered (WorldRenderEvents),
 *  - everything is written into ONE vertex buffer and drawn with ONE draw call on the GPU,
 *  - it goes through the game's own projection / view matrices, so a box can never slide off its block
 *    (FOV, sprint zoom, view bobbing, hurt tilt, aspect ratio are all handled by the game itself),
 *  - the pipeline has no depth test, so boxes show through walls.
 *
 * The old way (projecting points on the CPU and drawing hundreds of GUI rectangles) both drifted and was slow,
 * because every g.fill() is a separate GUI element in the newer renderer.
 *
 * Lines are drawn as thin camera-independent "ribbons" (two crossed quads per edge) whose width is kept
 * at roughly a constant number of screen pixels, so only one pipeline (quads) is needed.
 */
public final class WorldGfx {
    private WorldGfx() {}

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)   // = Mojang's DEBUG_FILLED_SNIPPET
                    .withLocation(Identifier.of("whiteclient", "pipeline/esp"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    /**
     * Shader pipeline for chunk markers (glowing squares / box faces / dots). Same pass setup as PIPELINE, but quads carry
     * UVs and a custom fragment shader (core/world_marker.fsh) draws constant-pixel-width glowing edges and a frosted fill.
     */
    private static final RenderPipeline SHADER_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("whiteclient", "pipeline/world_marker"))
                    .withVertexShader(Identifier.of("whiteclient", "core/ui_shape"))
                    .withFragmentShader(Identifier.of("whiteclient", "core/world_marker"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    private static final BufferAllocator ALLOCATOR = new BufferAllocator(1 << 18);
    private static final BufferAllocator SHADER_ALLOCATOR = new BufferAllocator(1 << 16);   // separate: two builders can't share one
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final Matrix4f MAT = new Matrix4f();
    private static final Vector3f TMP = new Vector3f();

    private static BufferBuilder buf;
    private static BufferBuilder sbuf;      // shader quads (position + uv + colour)
    private static double cx, cy, cz;       // camera position
    private static double pxPerUnit;        // world size of one framebuffer pixel, per block of distance
    private static float guiScale = 1f;

    /** Call once from the mod initializer so the pipeline is registered before first use. */
    public static void init() {}

    // ------------------------------------------------------------------ frame lifecycle

    /** Start collecting geometry for this frame. */
    public static void begin(MinecraftClient mc, MatrixStack stack, Vec3d cam) {
        MAT.set(stack.peek().getPositionMatrix());
        cx = cam.x; cy = cam.y; cz = cam.z;
        double fbH = Math.max(1, mc.getWindow().getFramebufferHeight());
        pxPerUnit = 2.0 * Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0) / fbH;
        guiScale = (float) mc.getWindow().getScaleFactor();
        buf = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
        sbuf = new BufferBuilder(SHADER_ALLOCATOR, SHADER_PIPELINE.getVertexFormatMode(), SHADER_PIPELINE.getVertexFormat());
    }

    /** Upload everything queued this frame and draw it: one call for the plain geometry, one for the shader markers. */
    public static void end(MinecraftClient mc) {
        BufferBuilder b = buf, sb = sbuf;
        buf = null;
        sbuf = null;
        flush(mc, b, PIPELINE, "whiteclient esp");
        flush(mc, sb, SHADER_PIPELINE, "whiteclient markers");
    }

    private static void flush(MinecraftClient mc, BufferBuilder b, RenderPipeline pipeline, String label) {
        if (b == null) return;
        BuiltBuffer built = b.endNullable();
        if (built == null) return;

        var dp = built.getDrawParameters();
        var format = dp.format();
        GpuBuffer vertices = format.uploadImmediateVertexBuffer(built.getBuffer());
        var shapeIndices = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
        GpuBuffer indices = shapeIndices.getIndexBuffer(dp.indexCount());
        var indexType = shapeIndices.getIndexType();

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        var fb = mc.getFramebuffer();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> label, fb.getColorAttachmentView(), OptionalInt.empty(),
                fb.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, dp.indexCount(), 1);
        }
        built.close();
    }

    public static boolean active() { return buf != null; }

    // ------------------------------------------------------------------ drawing API (world coordinates)

    /**
     * An axis-aligned box. line / fill are ARGB (fill alpha 0 = no fill). thickness is in the same
     * "1..3" units as the module settings. glow adds a soft wide halo around every edge.
     */
    public static void box(double x1, double y1, double z1, double x2, double y2, double z2,
                           int line, int fill, int thickness, boolean glow) {
        if (buf == null) return;
        if ((fill >>> 24) != 0) fillBox(x1, y1, z1, x2, y2, z2, fill);

        double dx = (x1 + x2) / 2 - cx, dy = (y1 + y2) / 2 - cy, dz = (z1 + z2) / 2 - cz;
        double dist = Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double w = dist * pxPerUnit * Math.max(1.5, thickness * guiScale * 0.75);   // ~constant screen width
        if (glow) edges(x1, y1, z1, x2, y2, z2, w * 3.5, (line & 0xFFFFFF) | (0x38 << 24));
        edges(x1, y1, z1, x2, y2, z2, w, line);
    }

    /**
     * Filled box with shaded faces (top brightest, bottom faintest) plus a normal outline: the "filled" hitbox look.
     * rgb has no alpha; fillAlpha is 0..255 for the brightest face (0 = outline only).
     */
    public static void shadedBox(double x1, double y1, double z1, double x2, double y2, double z2,
                                 int rgb, int fillAlpha, int line, int thickness, boolean glow) {
        if (buf == null) return;
        if (fillAlpha > 0) {
            int top = (Math.min(255, fillAlpha) << 24) | (rgb & 0xFFFFFF);
            int ns = ((fillAlpha * 4 / 5) << 24) | (rgb & 0xFFFFFF);
            int ew = ((fillAlpha * 3 / 5) << 24) | (rgb & 0xFFFFFF);
            int bot = ((fillAlpha * 2 / 5) << 24) | (rgb & 0xFFFFFF);
            quad(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, bot);   // bottom
            quad(x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, top);   // top
            quad(x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, ns);    // north
            quad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, ns);    // south
            quad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, ew);    // west
            quad(x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, ew);    // east
        }
        box(x1, y1, z1, x2, y2, z2, line, 0, thickness, glow);
    }

    /** A flat square on the horizontal plane y (outline + optional fill), e.g. a chunk footprint. */
    public static void square(double x1, double z1, double x2, double z2, double y, int line, int fill, int thickness) {
        if (buf == null) return;
        if ((fill >>> 24) != 0) quad(x1, y, z1, x2, y, z1, x2, y, z2, x1, y, z2, fill);
        double dx = (x1 + x2) / 2 - cx, dy = y - cy, dz = (z1 + z2) / 2 - cz;
        double dist = Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double w = dist * pxPerUnit * Math.max(1.5, thickness * guiScale * 0.75);
        ribbon(x1, y, z1, x2, y, z1, 0, w, line);
        ribbon(x1, y, z2, x2, y, z2, 0, w, line);
        ribbon(x1, y, z1, x1, y, z2, 2, w, line);
        ribbon(x2, y, z1, x2, y, z2, 2, w, line);
    }

    /** A small flat dot (constant on-screen size) centred on (x, y, z). */
    public static void dot(double x, double y, double z, int argb) {
        if (buf == null) return;
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double dist = Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double h = dist * pxPerUnit * 3.0 * guiScale;
        quad(x - h, y, z - h, x + h, y, z - h, x + h, y, z + h, x - h, y, z + h, argb);
    }

    // ------------------------------------------------------------------ shader markers (glowing, frosted)

    /**
     * Flat square on the horizontal plane y with glowing edges at a constant on-screen width and a frosted fill
     * (fillPct 0..100 = how strong the fill is). argb is the edge colour; thickness 1..4.
     */
    public static void glowSquare(double x1, double z1, double x2, double z2, double y, int argb, int fillPct, int thickness) {
        if (sbuf == null) return;
        int c = ShaderUi.pack(argb, (Math.round(Math.max(0, Math.min(100, fillPct)) * 15f / 100f) << 2) | Math.max(0, Math.min(3, thickness - 1)));
        squareQuad(x1, y, z1, x2, y, z1, x2, y, z2, x1, y, z2, c);
    }

    /** Box with glowing edges on all six faces (see through walls), plus a faint frosted fill. */
    public static void glowBox(double x1, double y1, double z1, double x2, double y2, double z2, int argb, int fillPct, int thickness) {
        if (sbuf == null) return;
        int c = ShaderUi.pack(argb, (Math.round(Math.max(0, Math.min(100, fillPct)) * 15f / 100f) << 2) | Math.max(0, Math.min(3, thickness - 1)));
        squareQuad(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, c);   // bottom
        squareQuad(x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, c);   // top
        squareQuad(x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, c);   // north
        squareQuad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, c);   // south
        squareQuad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, c);   // west
        squareQuad(x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, c);   // east
    }

    /** Round glowing dot (constant on-screen size) centred on (x, y, z). */
    public static void glowDot(double x, double y, double z, int argb) {
        if (sbuf == null) return;
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double dist = Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double h = dist * pxPerUnit * 5.0 * guiScale;
        int c = ShaderUi.pack(argb, 0);
        // u,v in 2..3 tells the shader "this quad is a dot"
        sv(x - h, y, z - h, 2f, 2f, c); sv(x + h, y, z - h, 3f, 2f, c); sv(x + h, y, z + h, 3f, 3f, c); sv(x - h, y, z + h, 2f, 3f, c);
    }

    /** One quad whose UVs run 0..1 (corner order a, b, c, d around the face). */
    private static void squareQuad(double ax, double ay, double az, double bx, double by, double bz,
                                   double cx2, double cy2, double cz2, double dx, double dy, double dz, int argb) {
        sv(ax, ay, az, 0f, 0f, argb); sv(bx, by, bz, 1f, 0f, argb); sv(cx2, cy2, cz2, 1f, 1f, argb); sv(dx, dy, dz, 0f, 1f, argb);
    }

    /** One shader vertex: same camera-relative transform as v(), plus UV. */
    private static void sv(double x, double y, double z, float u, float v, int argb) {
        TMP.set((float) (x - cx), (float) (y - cy), (float) (z - cz));
        MAT.transformPosition(TMP);
        sbuf.vertex(TMP.x, TMP.y, TMP.z).texture(u, v).color(argb);
    }

    // ------------------------------------------------------------------ internals

    private static void edges(double x1, double y1, double z1, double x2, double y2, double z2, double w, int c) {
        double[] xs = {x1, x2}, ys = {y1, y2}, zs = {z1, z2};
        for (int a = 0; a < 2; a++)
            for (int b = 0; b < 2; b++) {
                ribbon(x1, ys[a], zs[b], x2, ys[a], zs[b], 0, w, c);   // 4 edges along X
                ribbon(xs[a], y1, zs[b], xs[a], y2, zs[b], 1, w, c);   // 4 edges along Y
                ribbon(xs[a], ys[b], z1, xs[a], ys[b], z2, 2, w, c);   // 4 edges along Z
            }
    }

    /** One box edge as two crossed quads (a "+" in cross-section), so it has width from every direction. */
    private static void ribbon(double ax, double ay, double az, double bx, double by, double bz, int axis, double w, int c) {
        double h = w * 0.5;
        if (axis == 0) { ax -= h; bx += h; } else if (axis == 1) { ay -= h; by += h; } else { az -= h; bz += h; }   // close the corners
        for (int k = 0; k < 2; k++) {
            int perp = (axis + 1 + k) % 3;
            double ox = perp == 0 ? h : 0, oy = perp == 1 ? h : 0, oz = perp == 2 ? h : 0;
            quad(ax - ox, ay - oy, az - oz, bx - ox, by - oy, bz - oz, bx + ox, by + oy, bz + oz, ax + ox, ay + oy, az + oz, c);
        }
    }

    private static void fillBox(double x1, double y1, double z1, double x2, double y2, double z2, int c) {
        quad(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, c);   // bottom
        quad(x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, c);   // top
        quad(x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, c);   // north
        quad(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, c);   // south
        quad(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, c);   // west
        quad(x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, c);   // east
    }

    private static void quad(double ax, double ay, double az, double bx, double by, double bz,
                             double cx2, double cy2, double cz2, double dx, double dy, double dz, int argb) {
        v(ax, ay, az, argb); v(bx, by, bz, argb); v(cx2, cy2, cz2, argb); v(dx, dy, dz, argb);
    }

    /** One vertex: relative to the camera (keeps float precision high far from the origin), then through the stack matrix. */
    private static void v(double x, double y, double z, int argb) {
        TMP.set((float) (x - cx), (float) (y - cy), (float) (z - cz));
        MAT.transformPosition(TMP);
        buf.vertex(TMP.x, TMP.y, TMP.z).color(argb);
    }
}

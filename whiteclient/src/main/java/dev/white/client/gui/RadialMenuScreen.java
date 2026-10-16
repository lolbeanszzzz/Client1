package dev.white.client.gui;

import dev.white.client.module.impl.RadialMenu;
import dev.white.client.module.impl.SidebarUi;
import dev.white.client.module.impl.Hud;
import dev.white.client.util.SdfText;
import dev.white.client.util.ShaderUi;
import dev.white.client.util.Ui;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * The Radial Menu module's screen: a disc that opens up into a ring and splits into three slices (top, lower right,
 * lower left). Left click a slice = send its text / command, right click = type its text (Enter saves, Esc cancels),
 * Esc or the module key closes. Each slice is ONE shader quad (util/ShaderUi.slice, ui_slice*.fsh).
 */
public class RadialMenuScreen extends Screen {
    private static final Identifier CROWN = Identifier.of("whiteclient", "textures/gui/icons/crown.png");
    private static final double[] ANGLE = {Math.toRadians(-90), Math.toRadians(30), Math.toRadians(150)};   // slice centres
    private static final int TEXT = 0xFFEDF2FA, MUTED = 0xFF7C8AA3, FAINT = 0xFF56627A;

    private final RadialMenu module;
    private int editing;                 // slice being typed into, -1 = none
    private String buffer = "";
    private float openT;
    private final float[] hover = new float[3];
    private long lastNs;
    private float dt = 0.016f;
    private int cx, cy, outer;

    public RadialMenuScreen(RadialMenu module, int edit) {
        super(Text.literal("Radial Menu"));
        this.module = module;
        this.editing = edit;
        if (edit >= 0) buffer = module.slot(edit);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() { module.screenRemoved(); }

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float delta) {
        if (Hud.menuBlur()) super.renderBackground(g, mx, my, delta);
    }

    private static int mix(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private static int alpha(int argb, int a) { return (MathHelper.clamp(a, 0, 255) << 24) | (argb & 0xFFFFFF); }

    private int tw(String s) { return (int) Math.ceil(SdfText.width(SdfText.Face.SANS, s, SdfText.BASE)); }

    private String fit(String s, int max) {
        if (tw(s) <= max) return s;
        for (int n = s.length() - 1; n > 0; n--) {
            String t = s.substring(0, n).stripTrailing() + "...";
            if (tw(t) <= max) return t;
        }
        return "...";
    }

    private void text(DrawContext g, String s, float x, float y, float sc, int color, boolean bold) {
        SdfText.draw(g, bold ? SdfText.Face.BOLD : SdfText.Face.SANS, s, x, y, SdfText.BASE * sc, color);
    }

    /** Draws text centred on x at scale sc. */
    private void centered(DrawContext g, String s, float x, float y, float sc, int color, boolean bold) {
        SdfText.centered(g, bold ? SdfText.Face.BOLD : SdfText.Face.SANS, s, x, y, SdfText.BASE * sc, color);
    }

    /** Which slice is under (mx, my): -1 over the centre hole or outside the ring. */
    private int sliceAt(double mx, double my) {
        double dx = mx - cx, dy = my - cy, d = Math.sqrt(dx * dx + dy * dy);
        if (d < outer * 0.34 || d > outer + 16) return -1;
        int best = 0;
        double bd = -2;
        for (int i = 0; i < 3; i++) {
            double dot = (dx * Math.cos(ANGLE[i]) + dy * Math.sin(ANGLE[i])) / Math.max(d, 1e-6);
            if (dot > bd) { bd = dot; best = i; }
        }
        return best;
    }

    // palette for this frame (set in render): follows the menu's light / dark switch, or the sidebar menu's navy look
    private int cText, cMuted, cFaint, cDim, cEdgeA, cEdgeB, cOnA, cOnB, cOnText;
    private int cFillTop, cFillBottom, cEmptyTop, cEmptyBottom, cBox;

    private void palette() {
        Hud.applyTheme();                                    // make sure Theme matches the HUD colour / light-dark choice
        float sat = Math.max(0.45f, Hud.saturation());
        if (SidebarUi.active()) {                            // the sidebar menu is always dark navy
            int acc = 0xFF000000 | MathHelper.hsvToRgb(Hud.hue(), sat, 0.95f);
            int acc2 = 0xFF000000 | MathHelper.hsvToRgb((Hud.hue() + 0.09f) % 1f, Math.min(1f, sat * 0.9f), 1f);
            cText = TEXT; cMuted = MUTED; cFaint = FAINT; cDim = 0x55000000;
            cEdgeA = acc; cEdgeB = acc2; cOnA = alpha(acc, 0xE0); cOnB = alpha(acc2, 0xCC); cOnText = 0xFFFFFFFF;
            cFillTop = 0xE5162340; cFillBottom = 0xE50C1428; cEmptyTop = 0xE00F1829; cEmptyBottom = 0xE0090F1C;
            cBox = 0xE00A1222;
            return;
        }
        // classic menu: same colours as the windows, so dark mode is dark and light mode is light
        cText = Theme.TEXT;
        cMuted = mix(Theme.TEXT, Theme.MUTED, 0.55f);
        cFaint = mix(Theme.TEXT, Theme.MUTED, 0.8f);
        cDim = Theme.dark ? 0x55000000 : 0x55FFFFFF;
        cEdgeA = Theme.BORDER; cEdgeB = Theme.BORDER;
        cOnA = alpha(Theme.ON, 0xF0); cOnB = alpha(Theme.ON, 0xD8); cOnText = Theme.ON_TEXT;
        cFillTop = Theme.ROW; cFillBottom = Theme.ROW_HOVER;
        cEmptyTop = alpha(Theme.ROW, (Theme.ROW >>> 24) * 7 / 10); cEmptyBottom = alpha(Theme.ROW_HOVER, (Theme.ROW_HOVER >>> 24) * 7 / 10);
        cBox = Theme.ROW;
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        openT = Math.min(1f, openT + dt * 4.5f);
        float e = openT * openT * (3f - 2f * openT);

        palette();
        boolean sidebar = SidebarUi.active();

        cx = width / 2;
        cy = height / 2 - 8;
        outer = MathHelper.clamp(Math.min(width, height) / 2 - 44, 44, 84);
        g.fill(0, 0, width, height, cDim);

        float rOut = outer * (0.55f + 0.45f * e);
        float rIn = outer * 0.38f * e;                       // hole opens up from nothing: disc -> ring
        int hov = editing >= 0 ? editing : sliceAt(mx, my);

        for (int i = 0; i < 3; i++) {
            hover[i] += ((i == hov ? 1f : 0f) - hover[i]) * (1f - (float) Math.exp(-dt * 14f));
            float h = hover[i];
            float off = 6f * h * e;
            boolean filled = !module.slot(i).isEmpty();
            int baseTop = filled ? cFillTop : cEmptyTop, baseBottom = filled ? cFillBottom : cEmptyBottom;
            int top = mix(baseTop, cOnA, h), bottom = mix(baseBottom, cOnB, h * 0.8f);
            int edgeTop = sidebar ? alpha(cEdgeA, Math.round(0x40 + 0xBF * h)) : mix(alpha(cEdgeA, 0xA0), alpha(Theme.ON, 0xFF), h);
            int edgeBottom = sidebar ? alpha(cEdgeB, Math.round(0x30 + 0xAF * h)) : mix(alpha(cEdgeB, 0x90), alpha(Theme.ON, 0xFF), h);
            float sx = cx + (float) Math.cos(ANGLE[i]) * off, sy = cy + (float) Math.sin(ANGLE[i]) * off;
            ShaderUi.slice(g, false, i, sx, sy, rOut, rIn, mix(top, bottom, 0.5f));          // body: frosted gradient + rim light (shader)
            ShaderUi.slice(g, true, i, sx, sy, rOut, rIn, mix(edgeTop, edgeBottom, 0.5f));   // edge + soft glow (shader)
        }

        // labels
        float la = MathHelper.clamp((e - 0.55f) / 0.45f, 0f, 1f);
        if (la > 0.05f) {
            int a8 = Math.max(8, Math.round(255 * la));
            float rMid = (rIn + rOut) / 2f;
            for (int i = 0; i < 3; i++) {
                float h = hover[i];
                float off = 6f * h * e;
                float lx = cx + (float) Math.cos(ANGLE[i]) * (rMid + off), ly = cy + (float) Math.sin(ANGLE[i]) * (rMid + off);
                String t = i == editing ? buffer : module.slot(i);
                boolean empty = t.isEmpty();
                int slotCol = mix(cFaint, cOnText, h), mainCol = mix(empty ? cMuted : cText, cOnText, h), hintCol = mix(cFaint, cOnText, h * 0.8f);
                centered(g, "SLOT " + (i + 1), lx, ly - 13, 0.62f, alpha(slotCol, a8), true);
                centered(g, fit(empty ? "Empty" : t, 58), lx, ly - 3, 0.9f, alpha(mainCol, a8), !empty);
                if (empty) centered(g, "right click to set", lx, ly + 9, 0.6f, alpha(hintCol, a8), false);
            }
            // centre: logo + the full text of the hovered slice
            g.drawTexture(RenderPipelines.GUI_TEXTURED, CROWN, cx - 6, cy - 14, 0f, 0f, 12, 12, 32, 32, 32, 32, alpha(sidebar ? cEdgeA : Theme.TEXT, a8));
            String c = editing >= 0 ? "Slot " + (editing + 1) : hov >= 0 && !module.slot(hov).isEmpty() ? module.slot(hov) : "Radial Menu";
            centered(g, fit(c, (int) ((rIn * 2 - 6) / 0.7f)), cx, cy + 1, 0.7f, alpha(cText, a8), false);
        }

        // text box while typing a slice, hint line otherwise
        int by = cy + outer + 24;
        if (editing >= 0) {
            int bw = Math.min(300, width - 40), bx = cx - bw / 2;
            Ui.panel(g, bx, by, bx + bw, by + 16, 6, cBox, alpha(sidebar ? cEdgeA : Theme.ON, 0xCC), 0, false);   // shader text box
            String shown = buffer;
            while (!shown.isEmpty() && tw(shown) > bw - 16) shown = shown.substring(1);
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            text(g, shown.isEmpty() && !blink ? "Type text or /command" : shown + (blink ? "_" : ""), bx + 6, by + 4, 1f,
                    shown.isEmpty() ? cMuted : cText, false);
            centered(g, "Enter = save   Esc = cancel   start with / for a command", cx, by + 22, 0.7f, cMuted, false);
        } else if (la > 0.5f) {
            centered(g, "Click a slice to send   Right click = edit   Esc = close", cx, by, 0.75f, cMuted, false);
        }
    }

    // ---------------- input ----------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (editing >= 0) return true;                       // finish typing first
        int i = sliceAt(click.x(), click.y());
        if (i < 0) return super.mouseClicked(click, doubled);
        if (click.button() == 0) {
            if (module.slot(i).isEmpty()) { editing = i; buffer = ""; return true; }   // empty slice: left click starts typing too
            module.send(i);
            if (module.closeAfterSending()) close();
        } else if (click.button() == 1) {
            editing = i;
            buffer = module.slot(i);
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (editing < 0) return false;
        int cp = input.codepoint();
        if (!Character.isISOControl(cp) && cp != 0xA7 && buffer.length() < 256) buffer += new String(Character.toChars(cp));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.getKeycode();
        if (editing >= 0) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { module.setSlot(editing, buffer); editing = -1; }
            else if (key == GLFW.GLFW_KEY_ESCAPE) editing = -1;
            else if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!buffer.isEmpty()) buffer = buffer.substring(0, buffer.length() - 1); }
            else if (key == GLFW.GLFW_KEY_V && client != null && client.getWindow() != null
                    && (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS)) {
                String clip = GLFW.glfwGetClipboardString(client.getWindow().getHandle());   // Ctrl+V
                if (clip != null) for (char ch : clip.toCharArray())
                    if (!Character.isISOControl(ch) && ch != '§' && buffer.length() < 256) buffer += ch;
            }
            return true;
        }
        if (module.key > 0 && key == module.key) { close(); return true; }   // the module's own key closes it again
        return super.keyPressed(input);
    }
}

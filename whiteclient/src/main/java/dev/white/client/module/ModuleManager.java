package dev.white.client.module;

import dev.white.client.gui.BlobGuiScreen;
import dev.white.client.gui.ClickGuiScreen;
import dev.white.client.gui.ModuleGui;
import dev.white.client.gui.SidebarGuiScreen;
import dev.white.client.module.impl.*;
import dev.white.client.util.Gfx;
import dev.white.client.util.WorldGfx;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import dev.white.client.util.Projector;
import dev.white.client.util.ViewLock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    public final List<Module> modules = new ArrayList<>();
    private final Map<Module, Boolean> wasDown = new HashMap<>();
    private boolean guiKeyWasDown;

    public ModuleManager() {
        modules.add(new BlockEsp());
        modules.add(new Hitbox());
        modules.add(new Tracers());
        modules.add(new TriggerBot());
        modules.add(new AutoClicker());
        modules.add(new KillAura());
        modules.add(new SilentAura());
        modules.add(new BreachSwap());
        modules.add(new AutoMace());
        modules.add(new AutoWeapon());
        modules.add(new AutoBox());
        modules.add(new AutoCobweb());
        modules.add(new AimAssist());
        modules.add(new Surround());
        modules.add(new TotemPops());
        modules.add(new Hud());
        modules.add(new SidebarUi());
        modules.add(new BlobUi());
        modules.add(new Fullbright());
        modules.add(new UsernameHider());
        modules.add(new RadialMenu());
        modules.add(new PlayerInfo());
        modules.add(new SpotifyViewer());
        modules.add(new Freecam());
        modules.add(new DingDongBuilder());
        modules.add(new ToggleAll());
        modules.add(new Flight());
        modules.add(new Freeze());
        modules.add(new ElytraFly());
        modules.add(new NoFall());
        modules.add(new AutoMLG());
        modules.add(new PearlCatch());
        modules.add(new Sprint());
        modules.add(new KeepSprint());
        modules.add(new Bhop());
        modules.add(new Scaffold());
        modules.add(new AutoWalk());
        modules.add(new EntitySpeed());
        modules.add(new AutoTunnel());
        modules.add(new AutoEat());
        modules.add(new AutoTool());
        modules.add(new AutoFish());
        modules.add(new NoSlow());
        modules.add(new StashFinder());
        modules.add(new SusChunkFinder());
        modules.add(new NewChunks());
        modules.add(new LogoutSpots());
        modules.add(new Reach());
        modules.add(new HitboxExpand());
        modules.add(new AutoTotem());
        modules.add(new SafeWalk());
        modules.add(new Freelook());
        modules.add(new ChestStealer());
        modules.add(new InvSorter());
        modules.add(new AutoReplenish());
        modules.add(new AutoArmor());
        modules.add(new Criticals());
        modules.add(new Velocity());
        modules.add(new JumpReset());
        modules.add(new AutoShieldBreak());
        modules.add(new AutoDtap());
        modules.add(new FastCrystal());
        modules.add(new BaritoneControl());
        modules.add(new ElytraSwap());
        modules.add(new Macro());
        modules.add(new MacroRecord());
        modules.add(new Waypoints());
        modules.add(new Snowflakes());
        modules.add(new WaypointAdd());
        modules.add(new GeodeEsp());
        modules.add(new AncientDebris());
        modules.add(new FoundList());
        modules.add(new TrailTracker());
        modules.add(new SkyModule());
        modules.add(new SpearSwap());
        modules.add(new FriendsModule());
        modules.add(new AttackPackets());
        modules.add(new ConfigModule());
    }

    public List<Module> inCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules) if (m.category == c) out.add(m);
        return out;
    }

    public void onPreTick() {
        for (Module m : modules) if (m.isEnabled() && m.early()) m.onPreTick();
        for (Module m : modules) if (m.isEnabled() && !m.early()) m.onPreTick();
    }

    public void onTick() {
        pollKeys();
        // Sidebar UI switched on/off while a menu is open: swap to the matching menu straight away
        if (mc.currentScreen instanceof ModuleGui cur) {
            boolean blob = BlobUi.active(), side = !blob && SidebarUi.active();
            if (blob && !(cur instanceof BlobGuiScreen)) mc.setScreen(new BlobGuiScreen());
            else if (side && !(cur instanceof SidebarGuiScreen)) mc.setScreen(new SidebarGuiScreen());
            else if (!blob && !side && !(cur instanceof ClickGuiScreen)) mc.setScreen(new ClickGuiScreen());
        }
        for (Module m : modules) if (m.isEnabled()) m.onTick();
        for (Module m : modules) if (m.isEnabled()) m.onPostTick();
    }

    /** Modules that project things from the world onto the screen. */
    private boolean anyOverlay() {
        for (Module m : modules)
            if (m.isEnabled() && (m instanceof BlockEsp || m instanceof SusChunkFinder || m instanceof StashFinder
                    || m instanceof NewChunks || m instanceof LogoutSpots || m instanceof Waypoints
                    || m instanceof Tracers || m instanceof PlayerInfo || m instanceof AncientDebris || m instanceof GeodeEsp)) return true;
        return false;
    }

    /** Fired from the world render pass (WorldRenderEvents.BEFORE_TRANSLUCENT): queue and draw all world-space boxes. */
    public void onWorld(WorldRenderContext ctx) {
        if (mc.world == null || mc.player == null) return;
        if (!Projector.begin(mc)) return;
        WorldGfx.begin(mc, ctx.matrices(), Projector.camPos);
        try {
            for (Module m : modules) if (m.isEnabled()) m.onWorld();
        } finally {
            WorldGfx.end(mc);
        }
    }

    public void onHud(DrawContext g) {
        if (mc.world == null || mc.player == null) { ViewLock.release(mc); return; }
        ViewLock.update(mc, Hud.steadyView() && anyOverlay());
        if (!Projector.begin(mc)) return;
        Hud.applyTheme();
        Gfx.beginFrame();
        for (Module m : modules) if (m.isEnabled()) m.onHud(g);
    }

    /**
     * Called when the GUI screen itself handled the Right Shift press and closed. Marks the key as
     * already used so the next tick's poll does not see a fresh press and reopen the menu.
     */
    public void consumeGuiKey() { guiKeyWasDown = true; }

    private void pollKeys() {
        if (mc.getWindow() == null) return;
        long win = mc.getWindow().getHandle();

        boolean gui = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (gui && !guiKeyWasDown) {
            if (mc.currentScreen == null && mc.player != null) {
                mc.setScreen(BlobUi.active() ? new BlobGuiScreen() : SidebarUi.active() ? new SidebarGuiScreen() : new ClickGuiScreen());
            } else if (mc.currentScreen instanceof ModuleGui gs && !gs.isBinding()) {
                mc.currentScreen.close();   // same key toggles the menu closed
            }
        }
        guiKeyWasDown = gui;

        for (Module m : modules) {
            if (m.key <= 0) continue;
            boolean down = GLFW.glfwGetKey(win, m.key) == GLFW.GLFW_PRESS;
            boolean was = wasDown.getOrDefault(m, false);
            if (down && !was && mc.currentScreen == null) m.toggle();
            wasDown.put(m, down);
        }
    }
}

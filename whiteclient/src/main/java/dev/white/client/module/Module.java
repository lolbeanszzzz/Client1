package dev.white.client.module;

import dev.white.client.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public final String name;
    public final Category category;
    public final List<Setting> settings = new ArrayList<>();
    public int key = -1;          // GLFW key code, -1 = unbound
    public boolean expanded;      // settings dropdown open in the GUI
    private boolean enabled;

    protected Module(String name, Category category) {
        this.name = name;
        this.category = category;
    }

    protected <T extends Setting> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public boolean isEnabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (value) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    /** Start of every client tick (before the player ticks). */
    public void onPreTick() {}
    /** End of every client tick. */
    public void onTick() {}
    /** After every module's onTick has run (end of the client tick). */
    public void onPostTick() {}
    /** True = this module's onPreTick runs before everyone else's. */
    public boolean early() { return false; }
    /** Every frame, drawn as part of the HUD layer. */
    public void onHud(DrawContext g) {}
    /**
     * Every frame while the WORLD is being rendered. Queue ESP boxes here with WorldGfx.box(...) using real world
     * coordinates; they are drawn on the GPU through the game's own matrices (never drift, one draw call).
     */
    public void onWorld() {}
}

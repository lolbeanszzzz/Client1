package dev.white.client.module.impl;

import dev.white.client.WhiteClient;
import dev.white.client.module.Category;
import dev.white.client.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Master switch. Bind a key to it (middle-click it in the menu, then press a key).
 * Pressing it turns every module that is on off and remembers which ones; pressing it again turns
 * exactly those back on. If nothing was remembered it turns everything on except the modules that
 * only make sense when used deliberately (Freecam, Flight, Ding Dong Builder).
 */
public class ToggleAll extends Module {
    private final List<Module> remembered = new ArrayList<>();

    public ToggleAll() { super("Toggle All", Category.PLAYER); }

    @Override
    public void toggle() {
        List<Module> all = WhiteClient.modules.modules;

        List<Module> active = new ArrayList<>();
        for (Module m : all) if (m != this && !(m instanceof SidebarUi) && !(m instanceof BlobUi) && m.isEnabled()) active.add(m);

        if (!active.isEmpty()) {                       // something is on -> switch everything off
            remembered.clear();
            remembered.addAll(active);
            for (Module m : active) m.setEnabled(false);
            return;
        }

        if (!remembered.isEmpty()) {                   // all off -> bring back what was on
            for (Module m : remembered) m.setEnabled(true);
            remembered.clear();
            return;
        }

        for (Module m : all) {                         // nothing remembered -> enable everything sensible
            if (m == this || m instanceof SidebarUi || m instanceof BlobUi || m instanceof Freecam || m instanceof Flight || m instanceof DingDongBuilder
                    || m instanceof AutoWalk || m instanceof AutoTunnel
                    || m instanceof Freelook || m instanceof InvSorter || m instanceof WaypointAdd
                    || m instanceof Macro || m instanceof MacroRecord || m instanceof BaritoneControl || m instanceof AutoFish
                    || m instanceof ElytraSwap) continue;
            m.setEnabled(true);
        }
    }
}

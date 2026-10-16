package dev.white.client.module.impl;

import dev.white.client.gui.FoundListScreen;
import dev.white.client.module.Category;
import dev.white.client.module.Module;

/** Opens the Found List screen (also bindable to a key, and available as .finds). Switches itself straight back off. */
public class FoundList extends Module {
    public FoundList() { super("Found List", Category.BASE); }

    @Override
    protected void onEnable() {
        if (mc.player != null) mc.setScreen(new FoundListScreen(null));
        setEnabled(false);
    }
}

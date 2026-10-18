package dev.white.client.module.impl;

import dev.white.client.cape.CapeManager;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;

/** Local cape image picker. The button remains useful even while the module toggle is off. */
public final class Cape extends Module {
    public Cape() {
        super("Cape", Category.RENDER);
        add(new Setting.Button("Choose cape image", CapeManager::chooseCape));
        add(new Setting.Button("Remove custom cape", CapeManager::removeCape));
    }
}

package dev.white.client.module.impl;

import dev.white.client.command.CommandManager;
import dev.white.client.config.ConfigManager;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;

/** Quick config slots: save / load / delete up to 5 profiles from the GUI (same files as .config save/load). */
public class ConfigModule extends Module {
    private final Setting.Num slot = add(new Setting.Num("Slot", 1, 1, 5, 1));

    private final Setting.Bool withFriends = add(new Setting.Bool("Export friends", false));

    private String name() { return "slot" + slot.intValue(); }

    public ConfigModule() {
        super("Config", Category.MISC);
        add(new Setting.Button("Save slot", () ->
                CommandManager.say(ConfigManager.save(name()) ? "Saved to " + name() : "Couldn't save " + name())));
        add(new Setting.Button("Load slot", () ->
                CommandManager.say(ConfigManager.load(name()) ? "Loaded " + name() : "Nothing saved in " + name())));
        add(new Setting.Button("Delete slot", () ->
                CommandManager.say(ConfigManager.delete(name()) ? "Deleted " + name() : "Nothing saved in " + name())));
        add(new Setting.Button("Export to clipboard", () -> {
            String s = ConfigManager.exportString(withFriends.value);
            if (s.isEmpty()) { CommandManager.say("Export failed"); return; }
            mc.keyboard.setClipboard(s);
            CommandManager.say("Config copied to your clipboard (" + s.length() + " chars). Paste it anywhere to share.");
        }));
        add(new Setting.Button("Import from clipboard", () -> {
            String err = ConfigManager.importString(mc.keyboard.getClipboard());
            CommandManager.say(err == null ? "Config imported" : err);
        }));
        add(new Setting.Button("List profiles", () -> {
            var l = ConfigManager.list();
            CommandManager.say(l.isEmpty() ? "No saved profiles." : "Profiles: " + String.join(", ", l));
        }));
    }
}

package dev.white.client;

import dev.white.client.command.CommandManager;
import dev.white.client.config.ConfigManager;
import dev.white.client.module.ModuleManager;
import dev.white.client.cape.CapeManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.util.Identifier;

public class WhiteClient implements ClientModInitializer {
    public static ModuleManager modules;

    @Override
    public void onInitializeClient() {
        dev.white.client.util.ShaderUi.init();      // registers the UI shader pipelines before the first frame
        modules = new ModuleManager();
        CapeManager.loadSaved();
        ConfigManager.init();
        CommandManager.init();
        dev.white.client.util.WorldGfx.init();
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(ctx -> modules.onWorld(ctx));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            dev.white.client.util.SwapLimit.nextTick();
            dev.white.client.util.TickPhase.startTick();
            dev.white.client.module.impl.AttackPackets.flush();     // hits held back from the end of last tick go out first, before any movement packet
            modules.onPreTick();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> { modules.onTick(); ConfigManager.tick(); });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> dev.white.client.util.ViewLock.release(client));
        HudElementRegistry.addFirst(Identifier.of("whiteclient", "modules"),
                (context, tickCounter) -> modules.onHud(context));
    }
}

package dev.white.client.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a module send the "selected slot" packet at an exact point of a packet sequence. */
@Mixin(ClientPlayerInteractionManager.class)
public interface InteractionManagerInvoker {
    /** Sends the selected-slot packet if the held slot differs from the last one the server was told. */
    @Invoker("syncSelectedSlot")
    void white$syncSelectedSlot();
}

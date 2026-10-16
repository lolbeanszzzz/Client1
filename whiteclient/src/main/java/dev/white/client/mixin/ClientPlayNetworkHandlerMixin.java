package dev.white.client.mixin;

import dev.white.client.module.impl.NewChunks;
import dev.white.client.module.impl.Freeze;
import dev.white.client.module.impl.TotemPops;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import dev.white.client.module.impl.TotemPops;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the server's block updates to New Chunks. Injected at the TAIL so it only runs on the game thread
 * (the first, network-thread call of these handlers is thrown away by the game before it reaches the end).
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    /** Server moved us: Freeze lets the confirmation packets through and re-anchors. */
    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"), require = 0)
    private void white$teleport(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        Freeze.onServerTeleport();
    }

    /** Totem Pops: status 35 = a totem popped, status 3 = death. TAIL, so it runs on the game thread only. */
    @Inject(method = "onEntityStatus", at = @At("TAIL"), require = 0)
    private void white$entityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.world == null) return;
        TotemPops.onStatus(packet.getEntity(mc.world), packet.getStatus());
    }

    /** Totem Pops: status 35 = a totem popped, 3 = died. TAIL, so it only runs on the game thread. */
    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void white$status(EntityStatusS2CPacket packet, CallbackInfo ci) {
        net.minecraft.client.world.ClientWorld w = net.minecraft.client.MinecraftClient.getInstance().world;
        if (w == null) return;
        net.minecraft.entity.Entity e = packet.getEntity(w);
        if (e != null) TotemPops.onStatus(e, packet.getStatus());
    }

    /** Fast Crystal: a crystal that just appeared can be hit in the same tick. TAIL = game thread, entity exists. */
    @Inject(method = "onEntitySpawn", at = @At("TAIL"), require = 0)
    private void white$entitySpawn(net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket packet, CallbackInfo ci) {
        net.minecraft.client.world.ClientWorld w = net.minecraft.client.MinecraftClient.getInstance().world;
        if (w == null) return;
        net.minecraft.entity.Entity e = w.getEntityById(packet.getEntityId());
        if (e != null) dev.white.client.module.impl.FastCrystal.onSpawn(e);
    }

    /** Sound hook for Auto Fish. TAIL = game thread only. */
    @Inject(method = "onPlaySound", at = @At("TAIL"), require = 0)
    private void white$sound(net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket packet, CallbackInfo ci) {
        String path = packet.getSound().getKey().map(k -> k.getValue().getPath()).orElse("");
        dev.white.client.module.impl.AutoFish.onSound(path, packet.getX(), packet.getY(), packet.getZ());
    }

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void white$blockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        NewChunks.onBlockUpdate(packet.getPos(), packet.getState());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void white$deltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        packet.visitUpdates((pos, state) -> NewChunks.onBlockUpdate(pos, state));
    }
}

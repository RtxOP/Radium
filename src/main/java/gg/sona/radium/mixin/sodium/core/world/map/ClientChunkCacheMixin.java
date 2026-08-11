package gg.sona.radium.mixin.sodium.core.world.map;

import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkProviderClient.class)
public class ClientChunkCacheMixin {
    @Shadow
    private World worldObj;

    @Inject(
            method = "unloadChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;func_76623_d()V", remap = false,
                    shift = At.Shift.AFTER
            )
    )
    private void onChunkUnloaded(int x, int z, CallbackInfo ci) {
        ChunkTrackerHolder.get((WorldClient)this.worldObj)
                .onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
    }

    @Inject(
            method = "loadChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;func_177417_c(Z)V", remap = false,
                    shift = At.Shift.AFTER
            )
    )
    private void onChunkLoaded(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        ChunkTrackerHolder.get((WorldClient)this.worldObj)
                .onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
    }
}

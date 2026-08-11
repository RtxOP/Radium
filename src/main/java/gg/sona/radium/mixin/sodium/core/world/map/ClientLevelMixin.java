package gg.sona.radium.mixin.sodium.core.world.map;

import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;

@Mixin(WorldClient.class)
public abstract class ClientLevelMixin extends World implements ChunkTrackerHolder {
    @Unique
    private final ChunkTracker chunkTracker = new ChunkTracker();

    protected ClientLevelMixin(ISaveHandler saveHandler, WorldInfo levelProperties, WorldProvider dimension, Profiler profiler, boolean bl) {
        super(saveHandler, levelProperties, dimension, profiler, bl);
    }

    @Override
    public ChunkTracker sodium$getTracker() {
        return Objects.requireNonNull(this.chunkTracker);
    }
}

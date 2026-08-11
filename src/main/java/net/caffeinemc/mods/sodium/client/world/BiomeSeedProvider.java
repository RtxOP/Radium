package net.caffeinemc.mods.sodium.client.world;

import net.minecraft.client.multiplayer.WorldClient;

/**
 * Carries the zoom-stable biome seed that the modern Sodium uses to make biome
 * lookups resolution-independent. Set via mixin on {@code WorldClient} construction.
 */
public interface BiomeSeedProvider {
    static long getBiomeZoomSeed(WorldClient level) {
        return ((BiomeSeedProvider) level).sodium$getBiomeZoomSeed();
    }

    long sodium$getBiomeZoomSeed();
}

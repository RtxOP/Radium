package net.caffeinemc.mods.sodium.client.world.biome;

import dev.vexor.radium.compat.mojang.minecraft.BiomeColors;
import net.minecraft.world.biome.BiomeGenBase;

public enum BiomeColorSource {
    GRASS((biome, pos) -> biome.getGrassColorAtPos(pos)),
    FOLIAGE((biome, pos) -> biome.getFoliageColorAtPos(pos)),
    WATER((biome, pos) -> biome.waterColorMultiplier);

    private final BiomeColors.ColorProvider provider;

    BiomeColorSource(BiomeColors.ColorProvider provider) {
        this.provider = provider;
    }

    public BiomeColors.ColorProvider getProvider() {
        return provider;
    }

    public static final BiomeColorSource[] VALUES = BiomeColorSource.values();
    public static final int COUNT = VALUES.length;
}

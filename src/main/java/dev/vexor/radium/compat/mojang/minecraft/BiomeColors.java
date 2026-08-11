package dev.vexor.radium.compat.mojang.minecraft;

import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * MCP 1.8.9 stand-in for Yarn's {@code net.minecraft.client.color.world.BiomeColors}.
 * Radium's BiomeColorSource / LevelColorCache consume {@link ColorProvider} to resolve
 * per-position biome colors; 1.8.9 BiomeGenBase exposes the same numbers directly.
 */
public class BiomeColors {
    public interface ColorProvider {
        int getColorAtPos(BiomeGenBase biome, BlockPos pos);
    }

    private BiomeColors() {}
}

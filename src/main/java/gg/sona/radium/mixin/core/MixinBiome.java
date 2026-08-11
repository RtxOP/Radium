package gg.sona.radium.mixin.core;

import net.minecraft.world.biome.BiomeGenBase;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BiomeGenBase.class)
public class MixinBiome {
    @Shadow
    @Final
    private static BiomeGenBase[] biomeList;

    @Shadow
    @Final
    public static BiomeGenBase ocean;

    @Shadow
    @Final
    private static Logger logger;

    /**
     * @reason Fix out of bounds biome ID access
     * @author Lunasa
     */
    @Overwrite
    public static BiomeGenBase getBiomeFromBiomeList(int id, BiomeGenBase fallback) {
        if (id >= 0 && id < biomeList.length) {
            BiomeGenBase biome = biomeList[id];
            return biome == null ? fallback : biome;
        } else {
            logger.warn("BiomeGenBase ID is out of bounds: " + id + ", defaulting to 0 (Ocean)");
            return ocean;
        }
    }
}

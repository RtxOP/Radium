package net.caffeinemc.mods.sodium.client.render.chunk.terrain;

import net.minecraft.util.EnumWorldBlockLayer;

public class DefaultTerrainRenderPasses {
    public static final TerrainRenderPass SOLID = new TerrainRenderPass(false, false);
    public static final TerrainRenderPass CUTOUT = new TerrainRenderPass(false, true);
    public static final TerrainRenderPass CUTOUT_MIPPED = new TerrainRenderPass(false, true);
    public static final TerrainRenderPass TRANSLUCENT = new TerrainRenderPass(true, false);

    public static final TerrainRenderPass[] ALL = new TerrainRenderPass[] { SOLID, CUTOUT, CUTOUT_MIPPED, TRANSLUCENT };

    public static TerrainRenderPass fromLayer(EnumWorldBlockLayer layer) {
        switch (layer) {
            case SOLID:
                return SOLID;
            case CUTOUT_MIPPED:
                return CUTOUT_MIPPED;
            case CUTOUT:
                return CUTOUT;
            case TRANSLUCENT:
                return TRANSLUCENT;
            default:
                throw new IllegalArgumentException("Unsupported block layer: " + layer);
        }
    }
}

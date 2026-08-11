package net.caffeinemc.mods.sodium.client.render.chunk.terrain.material;

import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumWorldBlockLayer;

public class DefaultMaterials {
    public static final Material SOLID = new Material(DefaultTerrainRenderPasses.SOLID, AlphaCutoffParameter.ZERO, true);
    public static final Material CUTOUT = new Material(DefaultTerrainRenderPasses.CUTOUT, AlphaCutoffParameter.HALF, false);
    public static final Material CUTOUT_MIPPED = new Material(DefaultTerrainRenderPasses.CUTOUT_MIPPED, AlphaCutoffParameter.HALF, true);
    public static final Material TRANSLUCENT = new Material(DefaultTerrainRenderPasses.TRANSLUCENT, AlphaCutoffParameter.ZERO, true);

    public static Material forBlockState(IBlockState state) {
        return forRenderLayer(state.getBlock().getBlockLayer());
    }

    public static Material forFluidState(IBlockState state) {
        return forRenderLayer(state.getBlock().getBlockLayer());
    }

    public static Material forRenderLayer(EnumWorldBlockLayer layer) {
        switch (layer) {
            case SOLID:
                return SOLID;
            case CUTOUT:
                return CUTOUT;
            case CUTOUT_MIPPED:
                return CUTOUT_MIPPED;
            case TRANSLUCENT:
                return TRANSLUCENT;
            default:
                throw new IllegalArgumentException("Unsupported block layer: " + layer);
        }
    }
}

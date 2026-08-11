package net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.fluid;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;

public abstract class FluidRenderer {
    public abstract void render(LevelSlice level, IBlockState blockState, IBlockState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkBuildBuffers buffers);
}

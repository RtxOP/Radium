package net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.fluid;

import dev.vexor.radium.util.FluidSprites;
import net.caffeinemc.mods.sodium.client.model.color.ColorProviderRegistry;
import net.caffeinemc.mods.sodium.client.model.light.LightPipelineProvider;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;

public class FluidRendererImpl extends FluidRenderer {
    private final ColorProviderRegistry colorProviderRegistry;
    private final DefaultFluidRenderer defaultRenderer;

    private final FluidSprites sprites;

    public FluidRendererImpl(ColorProviderRegistry colorProviderRegistry, LightPipelineProvider lighters) {
        this.colorProviderRegistry = colorProviderRegistry;
        defaultRenderer = new DefaultFluidRenderer(lighters);
        sprites = new FluidSprites();
    }

    public void render(LevelSlice level, IBlockState blockState, IBlockState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkBuildBuffers buffers) {
        Material material = DefaultMaterials.forFluidState(fluidState);
        ChunkModelBuilder meshBuilder = buffers.get(material);
        BlockLiquid fluid = (BlockLiquid) fluidState.getBlock();

        defaultRenderer.render(level, blockState, blockPos, offset, collector, meshBuilder, material, colorProviderRegistry.getColorProvider(fluid), sprites.forFluid(fluid));
    }
}

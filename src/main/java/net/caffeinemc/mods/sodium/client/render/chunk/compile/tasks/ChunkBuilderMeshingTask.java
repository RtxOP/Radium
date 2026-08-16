package net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.MeshTaskSizeEstimator;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.DirectionalVisGraph;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.PresentTranslucentData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.Sorter;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.caffeinemc.mods.sodium.client.util.BlockRenderType;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ReportedException;
import net.minecraft.world.WorldType;
import org.joml.Vector3dc;

import java.util.Map;

/**
 * Rebuilds all the meshes of a chunk for each given render pass with non-occluded blocks. The result is then uploaded
 * to graphics memory on the main thread.
 * <p>
 * This task takes a slice of the level from the thread it is created on. Since these slices require rather large
 * array allocations, they are pooled to ensure that the garbage collector doesn't become overloaded.
 */
public class ChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
    private final ChunkRenderContext renderContext;
    private final SortBehavior sortBehavior;
    private final boolean forceSort;
    private final boolean blockingTask;

    public ChunkBuilderMeshingTask(RenderSection render, int buildTime, Vector3dc absoluteCameraPos, ChunkRenderContext renderContext, SortBehavior sortBehavior, boolean forceSort, boolean blockingTask) {
        super(render, buildTime, absoluteCameraPos);
        this.renderContext = renderContext;
        this.sortBehavior = sortBehavior;
        this.forceSort = forceSort;
        this.blockingTask = blockingTask;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext buildContext, CancellationToken cancellationToken) {
        BuiltSectionInfo.Builder renderData = new BuiltSectionInfo.Builder();
        DirectionalVisGraph occluder = new DirectionalVisGraph();

        ChunkBuildBuffers buffers = buildContext.buffers;
        buffers.init(renderData, this.section.getSectionIndex());

        BlockRenderCache cache = buildContext.cache;
        cache.init(this.renderContext);

        LevelSlice slice = cache.getWorldSlice();

        int minX = this.section.getOriginX();
        int minY = this.section.getOriginY();
        int minZ = this.section.getOriginZ();

        int maxX = minX + 16;
        int maxY = minY + 16;
        int maxZ = minZ + 16;

        // Initialise with minX/minY/minZ so initial getBlockState crash context is correct
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(minX, minY, minZ);
        BlockPos.MutableBlockPos modelOffset = new BlockPos.MutableBlockPos();

        boolean sortEnabled = this.sortBehavior != SortBehavior.OFF;
        TranslucentGeometryCollector collector = null;
        if (sortEnabled) {
            collector = new TranslucentGeometryCollector(this.section.getPosition(), this.sortBehavior);
        }
        BlockRenderContext context = new BlockRenderContext(slice, collector);

        try {
            gg.sona.radium.diag.Diag.columnProbe(this.section, slice);
            for (int y = minY; y < maxY; y++) {
                if (cancellationToken.isCancelled()) {
                    return null;
                }

                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        blockPos.set(x, y, z);
                        IBlockState blockState = slice.getBlockState(blockPos);
                        Block block = blockState.getBlock();

                        gg.sona.radium.diag.Diag.meshBlockProbe(this.section, block, x, y, z);
                        // MCP 1.8.9: legacy-yarn Block#getBlockType() maps to MCP Block#getRenderType(); both
// return the same render-type ints (3 = standard model, 2 = TESR, 1 = liquid, -1 = none).
int blockType = block.getRenderType();

                        if (blockType == BlockRenderType.INVISIBLE && !block.hasTileEntity()) {
                            continue;
                        }

                        // required for doors, fences, tripwires, etc.
                        // see: BlockRenderManager#getModel
                        if (slice.getWorldType() != WorldType.DEBUG_WORLD) {
                            blockState = block.getActualState(blockState, slice, blockPos);
                        }

                        int localX = x & 15;
                        int localY = y & 15;
                        int localZ = z & 15;
                        modelOffset.set(localX, localY, localZ);

                        if (blockType == BlockRenderType.MODEL) {
                            BlockRenderer renderer = cache.getBlockRenderer();

                            renderer.prepare(buffers);

                            IBakedModel model = cache.getBlockModels()
                                    .getModelForState(blockState);

                            context.update(blockPos, modelOffset, blockState, model);

                            renderer.renderModel(context);
                        }

                        if (blockType == BlockRenderType.LIQUID) {
                            cache.getFluidRenderer().render(slice, blockState, blockState, blockPos, modelOffset, collector, buffers);
                        }

                        if (block.hasTileEntity()) {
                            TileEntity entity = slice.getBlockEntity(blockPos);

                            if (entity != null) {
                                TileEntitySpecialRenderer renderer = TileEntityRendererDispatcher.instance.getSpecialRenderer(entity);

                                if (renderer != null) {
                                    // MCP 1.8.9 divergence: TileEntitySpecialRenderer#rendersOutsideBoundingBox() does
                                    // not exist in 1.8.9; the reference accounts for renderers that render outside
                                    // their bounding box. 1.8.9's TileEntityRendererDispatcher culls per axis aligned
                                    // bounding box, so the safe fallback is false (force in-section culling).
                                    renderData.addTileEntity(entity, true);
                                }
                            }
                        }

                        if (block.isOpaqueCube()) {
                            occluder.setOpaque(localX, localY, localZ);
                        }
                    }
                }
            }
        } catch (ReportedException ex) {
            // Propagate existing crashes (add context)
            throw fillCrashInfo(ex.getCrashReport(), slice, blockPos);
        } catch (Exception ex) {
            // Create a new crash report for other exceptions (e.g. thrown in getQuads)
            throw fillCrashInfo(CrashReport.makeCrashReport(ex, "Encountered exception while building chunk meshes"), slice, blockPos);
        }

        SortType sortType = SortType.NONE;
        if (sortEnabled) {
            sortType = collector.finishRendering();
        }

        // cancellation opportunity right before translucent sorting
        if (cancellationToken.isCancelled()) {
            return null;
        }

        boolean reuseUploadedData = false;
        TranslucentData translucentData = null;
        if (sortEnabled) {
            TranslucentData oldData = this.section.getTranslucentData();

            // Reusing non-dynamic data leads to attempting to sort with it again,
            // which throws an exception since it can only generate a sorter once.
            // To prevent this, reusing data is prevented when forceSort is enabled and the data is not dynamic.
            if (this.forceSort && !(oldData instanceof DynamicData)) {
                oldData = null;
            }

            translucentData = collector.getTranslucentData(oldData, this);
            reuseUploadedData = !this.forceSort && translucentData == oldData;
        }

        Map<TerrainRenderPass, BuiltSectionMeshParts> meshes = new Reference2ReferenceOpenHashMap<>();
        int visibleSlices = DefaultChunkRenderer.getVisibleFaces(
                (int) this.absoluteCameraPos.x(), (int) this.absoluteCameraPos.y(), (int) this.absoluteCameraPos.z(),
                this.section.getChunkX(), this.section.getChunkY(), this.section.getChunkZ());

        if (translucentData != null && translucentData.meshesWereModified()) {
            meshes.put(DefaultTerrainRenderPasses.TRANSLUCENT, buffers.createModifiedTranslucentMesh(translucentData.getUpdatedQuads()));
            renderData.addRenderPass(DefaultTerrainRenderPasses.TRANSLUCENT);
        }

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            if (meshes.containsKey(pass)) {
                continue;
            }

            // if the translucent geometry needs to share an index buffer between the directions,
            // consolidate all translucent geometry into UNASSIGNED
            boolean translucentBehavior = sortEnabled && pass.isTranslucent();
            boolean forceUnassigned = translucentBehavior && sortType.needsDirectionMixing;
            boolean sliceReordering = !translucentBehavior || sortType.allowSliceReordering;
            BuiltSectionMeshParts mesh = buffers.createMesh(pass, visibleSlices, forceUnassigned, sliceReordering);

            if (mesh != null) {
                meshes.put(pass, mesh);
                renderData.addRenderPass(pass);
            }
        }

        renderData.setOcclusionData(occluder.resolve());

        ChunkBuildOutput output = new ChunkBuildOutput(this.section, this.submitTime, translucentData, renderData.build(), meshes, blockingTask);

        if (sortEnabled) {
            if (reuseUploadedData) {
                output.markAsNotContainingNewIndexData();
            } else if (translucentData instanceof PresentTranslucentData) {
                PresentTranslucentData present = (PresentTranslucentData) translucentData;
                Sorter sorter = present.getSorter();
                sorter.writeIndexBuffer(this, true);
                output.setSorter(sorter);
            }
        }

        return output;
    }

    private ReportedException fillCrashInfo(CrashReport report, LevelSlice slice, BlockPos pos) {
        CrashReportCategory crashReportSection = report.makeCategory("Block being rendered");

        crashReportSection.addCrashSection("Chunk section", this.section);
        if (this.renderContext != null) {
            crashReportSection.addCrashSection("Render context volume", this.renderContext.volume());
        }

        return new ReportedException(report);
    }

    @Override
    public long estimateTaskSizeWith(MeshTaskSizeEstimator estimator) {
        return estimator.estimateSize(this.section);
    }
}
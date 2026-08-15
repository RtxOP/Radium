package net.caffeinemc.mods.sodium.client.render.chunk;

import dev.vexor.radium.compat.mojang.math.Mth;
import dev.vexor.radium.compat.mojang.minecraft.render.FogHelper;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferArena;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.async.CullResult;
import net.caffeinemc.mods.sodium.client.render.chunk.async.CullTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.JobDurationEstimator;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.JobEffort;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.LimitedResourceBudget;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.MeshResultSize;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.MeshTaskSizeEstimator;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UnlimitedResourceBudget;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UploadDuration;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UploadDurationEstimator;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.UploadResourceBudget;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJob;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderSortingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.*;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.*;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior.PriorityMode;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicSorter;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.NoData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.Sorter;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.SortTriggering;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.RemovableMultiForest;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import net.caffeinemc.mods.sodium.client.util.CameraUtils;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RenderSectionManager {
    private static final float NEARBY_REBUILD_DISTANCE = Mth.square(16.0f);
    private static final float IMMEDIATE_PRESENT_DISTANCE = Mth.square(64.0f);
    private static final float NEARBY_SORT_DISTANCE = Mth.square(25.0f);

    private static final float FRAME_DURATION_UPLOAD_FRACTION = 0.1f;
    private static final long MIN_UPLOAD_DURATION_BUDGET = 2_000_000L; // 2ms

    private final ChunkBuilder builder;

    private final RenderRegionManager regions;
    private final ClonedChunkSectionCache sectionCache;

    private final Long2ReferenceMap<RenderSection> sectionByPosition = new Long2ReferenceOpenHashMap<>();

    private final ConcurrentLinkedDeque<ChunkJobResult<? extends BuilderTaskOutput>> buildResults = new ConcurrentLinkedDeque<>();
    private final JobDurationEstimator jobDurationEstimator = new JobDurationEstimator();
    private final MeshTaskSizeEstimator meshTaskSizeEstimator;
    private final UploadDurationEstimator jobUploadDurationEstimator = new UploadDurationEstimator();
    private ChunkJobCollector lastBlockingCollector;
    private int thisFrameBlockingTasks;
    private int nextFrameBlockingTasks;
    private int deferredTasks;

    private final ChunkRenderer chunkRenderer;

    private final WorldClient level;

    private final ReferenceSet<RenderSection> sectionsWithGlobalEntities = new ReferenceOpenHashSet<>();

    private final OcclusionCuller occlusionCuller;

    private final int renderDistance;
    private final SortBehavior sortBehavior;

    private final SortTriggering sortTriggering;

    @NotNull
    private SortedRenderLists renderLists;

    private DeferredTaskList taskLists;
    private final EnumMap<DeferMode, ReferenceLinkedOpenHashSet<RenderSection>> importantTasks;

    private int frame;
    private long lastFrameDuration = -1;
    private long averageFrameDuration = -1;
    private long lastFrameAtTime = System.nanoTime();
    private static final float FRAME_DURATION_UPDATE_RATIO = 0.05f;

    private boolean needsGraphUpdate = true;
    private boolean needsRenderListUpdate = true;
    private boolean cameraChanged = false;

    private @Nullable Vector3dc cameraPosition;

    private final ExecutorService asyncCullExecutor = Executors.newSingleThreadExecutor(RenderSectionManager::makeAsyncCullThread);
    private CullTask pendingTask = null;

    private SectionTree renderTree = null;
    private final Map<CullType, SectionTree> cullResults = new EnumMap<>(CullType.class);
    private final RemovableMultiForest renderableSectionTree;

    private final AsyncCameraTimingControl cameraTimingControl = new AsyncCameraTimingControl();

    public RenderSectionManager(WorldClient level, int renderDistance, SortBehavior sortBehavior, CommandList commandList) {
        this.meshTaskSizeEstimator = new MeshTaskSizeEstimator(level);

        this.chunkRenderer = new DefaultChunkRenderer(RenderDevice.INSTANCE, ChunkMeshFormats.COMPACT);

        this.level = level;
        this.builder = new ChunkBuilder(level, ChunkMeshFormats.COMPACT);
        gg.sona.radium.diag.Diag.once("rsm-ctor", "RenderSectionManager ctor: renderDistance=" + renderDistance
                + " asyncCulling=" + this.useAsyncCulling() + " builderThreads=" + this.builder.getTotalThreadCount());

        this.renderDistance = renderDistance;
        this.sortBehavior = sortBehavior;

        if (this.sortBehavior != SortBehavior.OFF) {
            this.sortTriggering = new SortTriggering();
        } else {
            this.sortTriggering = null;
        }

        this.regions = new RenderRegionManager(commandList);
        this.sectionCache = new ClonedChunkSectionCache(this.level);

        this.renderLists = SortedRenderLists.empty();
        this.occlusionCuller = new OcclusionCuller(Long2ReferenceMaps.unmodifiable(this.sectionByPosition), this.level);

        this.renderableSectionTree = new RemovableMultiForest(renderDistance);

        this.importantTasks = new EnumMap<>(DeferMode.class);
        for (DeferMode deferMode : DeferMode.values()) {
            this.importantTasks.put(deferMode, new ReferenceLinkedOpenHashSet<>());
        }
    }

    public void prepareFrame(Vector3dc cameraPosition) {
        this.cameraPosition = cameraPosition;

        long now = System.nanoTime();
        this.lastFrameDuration = now - this.lastFrameAtTime;
        this.lastFrameAtTime = now;
        if (this.averageFrameDuration == -1) {
            this.averageFrameDuration = this.lastFrameDuration;
        } else {
            this.averageFrameDuration = MathUtil.exponentialMovingAverage(this.averageFrameDuration, this.lastFrameDuration, FRAME_DURATION_UPDATE_RATIO);
        }
        this.averageFrameDuration = Mth.clamp(this.averageFrameDuration, 1_000_100, 100_000_000);
    }

    public void prepareRender() {
        this.frame += 1;
        if (this.cameraChanged) {
            this.invalidateRenderLists();
        }
    }

    public void prepareRenderTrees(Viewport viewport, boolean spectator) {
        if (!this.useAsyncCulling()) {
            this.prepareRenderTreesSync(viewport, spectator);
            return;
        }

        if (this.pendingTask != null && (this.cameraChanged || this.needsGraphUpdate)) {
            this.pendingTask.setCancelled();

            // Drop tasks that never started immediately so the updated view can be submitted
            // without waiting for an obsolete queued task to finish.
            if (this.pendingTask.cancelIfNotStarted()) {
                this.pendingTask = null;
            }
        }

        // consume the results of completed tasks
        this.consumeCullTaskResults(false);

        // discard unusable present and pending frustum-tested trees
        if (this.cameraChanged) {
            this.cullResults.remove(CullType.LOCAL);
        }

        // if the origin exists in the graph, schedule new async culling task
        if (!this.isOutOfGraph(viewport.getChunkCoord()) && (this.cameraChanged || this.needsGraphUpdate)) {
            this.scheduleAsyncWork(viewport, spectator);
        }
    }

    private void prepareRenderTreesSync(Viewport viewport, boolean spectator) {
        if (this.pendingTask != null) {
            this.pendingTask.setCancelled();
            this.pendingTask = null;
        }

        if (this.cameraChanged) {
            this.cullResults.remove(CullType.LOCAL);
        }

        if (this.isOutOfGraph(viewport.getChunkCoord()) || !(this.cameraChanged || this.needsGraphUpdate)) {
            return;
        }

        float searchDistanceRegular = this.getSearchDistanceForCullType(CullType.REGULAR);
        float searchDistanceLocal = this.getSearchDistanceForCullType(CullType.LOCAL);
        boolean useOcclusionCulling = this.shouldUseOcclusionCulling(spectator);

        TaskCollectingTree wideTree = new TaskCollectingTree(viewport, searchDistanceRegular, this.frame, CullType.WIDE, this.level);
        SectionTree regularTree = new SectionTree(viewport, searchDistanceRegular, this.frame, CullType.REGULAR, this.level);
        RayOcclusionSectionTree localTree = new RayOcclusionSectionTree(viewport, searchDistanceLocal, this.frame, CullType.LOCAL, this.level);
        SyncVisibleChunkCollector visibleCollector = new SyncVisibleChunkCollector(this.frame);

        this.occlusionCuller.findVisible(
                wideTree,
                regularTree,
                new SyncLocalTreeCollector(localTree, visibleCollector),
                viewport,
                searchDistanceRegular,
                searchDistanceLocal,
                useOcclusionCulling,
                CancellationToken.NEVER_CANCELLED
        );

        wideTree.prepareForTraversal();
        regularTree.prepareForTraversal();
        localTree.prepareForTraversal();

        this.cullResults.put(CullType.LOCAL, localTree);
        this.cullResults.put(CullType.REGULAR, regularTree);
        this.cullResults.put(CullType.WIDE, wideTree);
        this.taskLists = wideTree.getPendingTaskLists();
        this.renderLists = visibleCollector.createRenderLists(viewport);
        this.renderTree = localTree;
        this.needsGraphUpdate = false;
        this.needsRenderListUpdate = false;
    }

    public void finalizeRenderLists(Viewport viewport, boolean updateChunksImmediately) {
        boolean syncRender = this.cameraTimingControl.getShouldRenderSync();
        gg.sona.radium.diag.Diag.throttle("finalize", "finalizeRenderLists updateImmediate=" + updateChunksImmediately
                + " syncRender=" + syncRender
                + " needsList=" + this.needsRenderListUpdate
                + " needsGraph=" + this.needsGraphUpdate
                + " pendingTask=" + (this.pendingTask == null ? -1 : (this.pendingTask.isDone() ? 1 : 0)));
        if (updateChunksImmediately) {
            this.renderOutOfGraph(viewport);
        } else if (this.needsRenderListUpdate) {
            if (syncRender && this.pendingTask != null) {
                this.consumeCullTaskResults(true);
            }

            this.readRenderListFromTree(viewport);
        }

        this.needsRenderListUpdate = false;
        this.cameraChanged = false;
    }

    private void consumeCullTaskResults(boolean waitForCompletion) {
        CullTask task = this.pendingTask;
        if (task == null) {
            return;
        }

        // if there's a waiting viewport, don't skip unfinished task
        if (!waitForCompletion && !task.isDone()) {
            return;
        }

        CullResult result = task.getResult();
        this.pendingTask = null;

        if (result == null || task.isCancelled()) {
            return;
        }

        this.acceptCullResult(result);
    }

    private static Thread makeAsyncCullThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setName("Sodium Async Cull Thread");
        return thread;
    }
    private void scheduleAsyncWork(Viewport viewport, boolean spectator) {
        if (this.pendingTask != null) {
            return;
        }
        // submit cull task if there's none running currently
        float searchDistanceRegular = this.getSearchDistanceForCullType(CullType.REGULAR);
        float searchDistanceLocal = this.getSearchDistanceForCullType(CullType.LOCAL);

        boolean useOcclusionCulling = this.shouldUseOcclusionCulling(spectator);
        this.pendingTask = new CullTask(viewport, searchDistanceRegular, searchDistanceLocal, this.frame, this.occlusionCuller, useOcclusionCulling, this.level);
        this.pendingTask.submitTo(this.asyncCullExecutor);

        // only clear the graph update if we actually scheduled a task. Otherwise, the currently running task might not pick up on the change and no additional task would have been scheduled.
        this.needsGraphUpdate = false;
    }

    private CullResult createCullResult(Viewport viewport, boolean spectator, CancellationToken cancellationToken) {
        float searchDistanceRegular = this.getSearchDistanceForCullType(CullType.REGULAR);
        float searchDistanceLocal = this.getSearchDistanceForCullType(CullType.LOCAL);
        boolean useOcclusionCulling = this.shouldUseOcclusionCulling(spectator);

        TaskCollectingTree wideTree = new TaskCollectingTree(viewport, searchDistanceRegular, this.frame, CullType.WIDE, this.level);
        SectionTree regularTree = new SectionTree(viewport, searchDistanceRegular, this.frame, CullType.REGULAR, this.level);
        RayOcclusionSectionTree localTree = new RayOcclusionSectionTree(viewport, searchDistanceLocal, this.frame, CullType.LOCAL, this.level);

        this.occlusionCuller.findVisible(wideTree, regularTree, localTree, viewport, searchDistanceRegular, searchDistanceLocal, useOcclusionCulling, cancellationToken);

        wideTree.prepareForTraversal();
        regularTree.prepareForTraversal();
        localTree.prepareForTraversal();

        DeferredTaskList taskLists = wideTree.getPendingTaskLists();

        return new CullResult() {
            @Override
            public SectionTree getCullTreeWide() {
                return wideTree;
            }

            @Override
            public SectionTree getCullTreeRegular() {
                return regularTree;
            }

            @Override
            public SectionTree getCullTreeLocal() {
                return localTree;
            }

            @Override
            public DeferredTaskList getPendingTaskLists() {
                return taskLists;
            }
        };
    }

    private void acceptCullResult(CullResult result) {
        if (result == null) {
            return;
        }

        this.cullResults.put(CullType.LOCAL, result.getCullTreeLocal());
        this.cullResults.put(CullType.REGULAR, result.getCullTreeRegular());
        this.cullResults.put(CullType.WIDE, result.getCullTreeWide());
        this.taskLists = result.getPendingTaskLists();
        this.invalidateRenderLists();

        DeferredTaskList pending = result.getPendingTaskLists();
        gg.sona.radium.diag.Diag.throttle("cullResult", "cullResult frame=" + this.frame
                + " wide=" + countSections(result.getCullTreeWide())
                + " reg=" + countSections(result.getCullTreeRegular())
                + " loc=" + countSections(result.getCullTreeLocal())
                + " pending=" + (pending == null ? -1 : pending.size()));
    }

    private static int countSections(SectionTree tree) {
        return tree.tree.countSections();
    }

    private boolean useAsyncCulling() {
        return SodiumClientMod.options().performance.useAsyncCulling;
    }

    private SectionTree findBestTree(Viewport viewport) {
        for (CullType type : CullType.NARROW_TO_WIDE) {
            SectionTree tree = this.cullResults.get(type);
            if (tree == null) {
                continue;
            }

            float searchDistance = this.getSearchDistanceForCullType(type);
            if (tree.isValidFor(viewport, searchDistance)) {
                return tree;
            }
        }

        return null;
    }

    private void readRenderListFromTree(Viewport viewport) {
        // pick the narrowest available tree
        SectionTree bestTree = this.findBestTree(viewport);

        // use out-of-graph fallback if the origin section is not loaded and there's no valid tree (missing origin section, empty world)
        if (bestTree == null && this.isOutOfGraph(viewport.getChunkCoord())) {
            this.renderOutOfGraph(viewport);
            return;
        }

        // wait for pending tasks to maybe supply a valid tree if there's no current tree (first frames after initial load/reload)
        if (bestTree == null) {
            this.consumeCullTaskResults(true);
            bestTree = this.findBestTree(viewport);
        }

        if (bestTree == null) {
            this.renderOutOfGraph(viewport);
            return;
        }

        VisibleChunkCollector visibleCollector = new VisibleChunkCollector(this.regions, this.frame);
        bestTree.traverse(visibleCollector, viewport, this.getSearchDistance());
        this.renderLists = visibleCollector.createRenderLists(viewport);

        this.renderTree = bestTree;

        this.logRenderListStats("tree");
    }

    private void logRenderListStats(String path) {
        int regionCount = 0;
        int sectionCount = 0;
        java.util.Iterator<ChunkRenderList> it = this.renderLists.iterator(false);
        while (it.hasNext()) {
            ChunkRenderList list = it.next();
            regionCount++;
            sectionCount += list.getSectionsWithGeometryCount();
        }
        gg.sona.radium.diag.Diag.throttle("renderLists", "renderLists path=" + path
                + " regions=" + regionCount + " sections=" + sectionCount);
    }

    private void renderOutOfGraph(Viewport viewport) {
        float searchDistance = this.getSearchDistance();
        FallbackVisibleChunkCollector visitor = new FallbackVisibleChunkCollector(viewport, searchDistance, this.frame, this.sectionByPosition, this.regions, this.level);

        this.renderableSectionTree.prepareForTraversal();
        this.renderableSectionTree.traverse(visitor, viewport, searchDistance);

        this.renderLists = visitor.createRenderLists(viewport);
        this.taskLists = visitor.getPendingTaskLists();

        visitor.prepareForTraversal();
        this.renderTree = visitor;

        this.logRenderListStats("fallback");
    }
    private boolean isOutOfGraph(SectionPos pos) {
        int sectionY = pos.getY();
        return 0 <= sectionY && sectionY <= 16 && !this.sectionByPosition.containsKey(pos.asLong());
    }

    private float getSearchDistance() {
        float distance;

        if (SodiumClientMod.options().performance.useFogOcclusion) {
            distance = this.getEffectiveRenderDistance();
        } else {
            distance = this.getRenderDistance();
        }

        return distance;
    }

    private boolean shouldUseOcclusionCulling(boolean spectator) {
        final boolean useOcclusionCulling;
        BlockPos origin = CameraUtils.getBlockPosition();

        if (spectator && this.level.getBlockState(origin).getBlock().isFullBlock()) {
            useOcclusionCulling = false;
        } else {
            useOcclusionCulling = SodiumClientMod.options().performance.smartCull;
        }
        return useOcclusionCulling;
    }

    public void beforeSectionUpdates() {
        this.renderableSectionTree.ensureCapacity(this.getRenderDistance());
    }

    public void markGraphDirty() {
        this.needsGraphUpdate = true;
    }

    public void notifyChangedCamera() {
        this.cameraChanged = true;
    }

    public boolean needsUpdate() {
        return this.needsGraphUpdate && this.pendingTask == null;
    }

    private void invalidateRenderLists() {
        this.needsRenderListUpdate = true;
    }

    private float getSearchDistanceForCullType(CullType cullType) {
        if (cullType.isFogCulled) {
            return this.getSearchDistance();
        } else {
            return this.getRenderDistance();
        }
    }
    public void onSectionAdded(int x, int y, int z) {
        long key = SectionPos.asLong(x, y, z);

        if (this.sectionByPosition.containsKey(key)) {
            return;
        }

        RenderRegion region = this.regions.createForChunk(x, y, z);

        RenderSection renderSection = new RenderSection(region, x, y, z);
        region.addSection(renderSection);

        this.sectionByPosition.put(key, renderSection);

        Chunk chunk = this.level.getChunkFromChunkCoords(x, z);
        ExtendedBlockStorage section = chunk.getBlockStorageArray()[y];

        if (section == null || section.isEmpty()) {
            this.updateSectionInfo(renderSection, BuiltSectionInfo.EMPTY);
        } else {
            this.renderableSectionTree.add(renderSection);
            renderSection.setPendingUpdate(ChunkUpdateTypes.INITIAL_BUILD, this.lastFrameAtTime);
        }

        this.connectNeighborNodes(renderSection);

        // force update to schedule build task
        this.markGraphDirty();
    }

    public void onSectionRemoved(int x, int y, int z) {
        long sectionPos = SectionPos.asLong(x, y, z);
        RenderSection section = this.sectionByPosition.remove(sectionPos);

        if (section == null) {
            return;
        }

        this.renderableSectionTree.remove(x, y, z);

        if (section.getTranslucentData() != null) {
            this.sortTriggering.removeSection(section.getTranslucentData(), sectionPos);
        }

        RenderRegion region = section.getRegion();

        if (region != null) {
            region.removeSection(section);
        }

        this.disconnectNeighborNodes(section);
        this.updateSectionInfo(section, null);

        section.delete();

        // force update to remove section from render lists
        this.markGraphDirty();
    }

    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z) {
        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        this.chunkRenderer.render(matrices, commandList, this.renderLists, pass, new CameraTransform(x, y, z), this.sortBehavior != SortBehavior.OFF);

        commandList.flush();
        int diagListCount = 0;
        java.util.Iterator<ChunkRenderList> diagIt = this.renderLists.iterator(false);
        while (diagIt.hasNext()) {
            diagIt.next();
            diagListCount++;
        }
        gg.sona.radium.diag.Diag.throttle("renderLayer", "renderLayer pass=" + pass + " lists=" + diagListCount);
    }

    public void tickVisibleRenders() {
        Iterator<ChunkRenderList> it = this.renderLists.iterator();

        while (it.hasNext()) {
            ChunkRenderList renderList = it.next();

            RenderRegion region = renderList.getRegion();
            ByteIterator iterator = renderList.sectionsWithSpritesIterator();

            if (iterator == null) {
                continue;
            }

            while (iterator.hasNext()) {
                TextureAtlasSprite[] sprites = region.getAnimatedSprites(iterator.nextByteAsInt());

                if (sprites == null) {
                    continue;
                }

                for (TextureAtlasSprite sprite : sprites) {
                    SpriteUtil.markSpriteActive(sprite);
                }
            }
        }
    }

    private boolean isSectionEmpty(int x, int y, int z) {
        long key = SectionPos.asLong(x, y, z);
        RenderSection section = this.sectionByPosition.get(key);

        if (section == null) {
            return true;
        }

        return !section.needsRender();
    }

    // renderTree is not necessarily frustum-filtered but that is ok since the caller makes sure to eventually also perform a frustum test on the box being tested (see EntityRendererMixin)
    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        return this.renderTree == null || this.renderTree.isBoxVisible(x1, y1, z1, x2, y2, z2, this::isSectionEmpty);
    }

    public void processChunkBuilds(Viewport viewport) {
        ArrayList<BuilderTaskOutput> results = this.collectChunkBuildResults();

        if (results.isEmpty()) {
            return;
        }

        // processing build results can cause invalidation of the render lists or change the connectivity of the graph. They don't necessarily imply each other, so they're tracked separately.
        int changes = this.processChunkBuildResults(results, viewport);
        if ((changes & SectionInfoChange.GRAPH) != 0) {
            this.markGraphDirty();
        }
        if ((changes & SectionInfoChange.RENDER_LIST) != 0) {
            this.invalidateRenderLists();
        }

        for (BuilderTaskOutput result : results) {
            result.destroy();
        }
    }
    private boolean isSectionFrustumVisible(Viewport viewport, RenderSection section) {
        // unloaded sections are considered visible as to not be an impossible requirement for immediate presentation
        return section == null || this.renderTree == null || this.renderTree.isSectionVisible(viewport, section);
    }
    private int processChunkBuildResults(ArrayList<BuilderTaskOutput> results, Viewport viewport) {
        List<RenderSection> sectionsWithOutputs = applyBuildOutputs(results);
        ArrayList<BuilderTaskOutput> outputs = new ArrayList<BuilderTaskOutput>();

        // prepare list of pending present patches if there are pending tasks that will need patches
        List<RenderSection> pendingPresentPatches = null;
        if (this.pendingTask != null) {
            pendingPresentPatches = new ReferenceArrayList<>();
        }

        int changes = SectionInfoChange.NONE;
        long totalUploadSize = 0;
        for (RenderSection section : sectionsWithOutputs) {
            ChunkBuildOutput buildOutput = section.retrievePendingBuildOutput();
            if (buildOutput != null) {
                long resultSize = buildOutput.getResultSize();
                gg.sona.radium.diag.Diag.count("sectionBuilt", "sectionBuilt size=" + resultSize
                        + " section=" + section.getChunkX() + "," + section.getChunkY() + "," + section.getChunkZ());
                TranslucentData oldData = section.getTranslucentData();
                changes |= updateWithResult(viewport, section, buildOutput, pendingPresentPatches);
                section.setLastMeshResultSize(resultSize);
                this.meshTaskSizeEstimator.addData(this.meshTaskSizeEstimator.resultForSection(section, resultSize));

                if (buildOutput.translucentData != null) {
                    this.sortTriggering.integrateTranslucentData(oldData, buildOutput.translucentData, this.cameraPosition, this::scheduleSort);
                    section.setTranslucentData(buildOutput.translucentData);
                }
                outputs.add(buildOutput);
                totalUploadSize += resultSize;
            }
            ChunkSortOutput sortOutput = section.retrievePendingDynamicSortOutput(buildOutput);
            if (sortOutput != null) {
                TranslucentData translucentData = section.getTranslucentData();
                if (translucentData instanceof DynamicData) {
                    DynamicData dynamicData = (DynamicData) translucentData;
                    Sorter sorter = sortOutput.getSorter();
                    if (sorter instanceof DynamicSorter && dynamicData.isMatchingSorter((DynamicSorter) sorter)) {
                        if (dynamicData instanceof DynamicTopoData) {
                            DynamicTopoData data = (DynamicTopoData) dynamicData;
                            if (sorter instanceof DynamicTopoData.DynamicTopoSorter) {
                                DynamicTopoData.DynamicTopoSorter topoSorter = (DynamicTopoData.DynamicTopoSorter) sorter;
                                this.sortTriggering.applyTopoSortingTriggerChanges(data, topoSorter, section.getPosition(), this.cameraPosition);
                            }
                        }
                        outputs.add(sortOutput);
                        totalUploadSize += sortOutput.getResultSize();
                    }
                }
            }
        }
        this.meshTaskSizeEstimator.updateModels();

        if (pendingPresentPatches != null && !pendingPresentPatches.isEmpty() &&
                this.pendingTask != null) {
            this.pendingTask.registerPresentPatches(pendingPresentPatches);
        }

        long uploadStart = System.nanoTime();
        this.regions.uploadResults(RenderDevice.INSTANCE.createCommandList(), outputs);
        long uploadDuration = System.nanoTime() - uploadStart;

        if (!outputs.isEmpty()) {
            int outputCount = outputs.size();
            this.jobUploadDurationEstimator.addData(new UploadDuration(uploadDuration / outputCount, totalUploadSize / outputCount));
            this.jobUploadDurationEstimator.updateModels();
        }

        // Diag: log pipeline counts (throttled)
        gg.sona.radium.diag.Diag.throttle("buildResults", "buildResults results=" + results.size()
                + " sections=" + sectionsWithOutputs.size() + " uploads=" + outputs.size()
                + " totalUploadSize=" + totalUploadSize);

        return changes;
    }

    private boolean isSectionImmediatePresentationCandidate(Viewport viewport, RenderSection section) {
        if (this.cameraPosition == null) {
            return false;
        }
        float distanceSquared = section.getSquaredDistance(
                (float) this.cameraPosition.x(),
                (float) this.cameraPosition.y(),
                (float) this.cameraPosition.z()
        );

        if (distanceSquared < NEARBY_REBUILD_DISTANCE) {
            return true;
        }

        return distanceSquared < IMMEDIATE_PRESENT_DISTANCE &&
                // check that visible or adjacent to a visible section
                (this.isSectionFrustumVisible(viewport, section)
                        || this.isSectionFrustumVisible(viewport, section.adjacentDown)
                        || this.isSectionFrustumVisible(viewport, section.adjacentUp)
                        || this.isSectionFrustumVisible(viewport, section.adjacentNorth)
                        || this.isSectionFrustumVisible(viewport, section.adjacentSouth)
                        || this.isSectionFrustumVisible(viewport, section.adjacentWest)
                        || this.isSectionFrustumVisible(viewport, section.adjacentEast));
    }

    private int updateWithResult(Viewport viewport, RenderSection section, ChunkBuildOutput chunkBuildOutput, List<RenderSection> pendingPresentPatches) {
        int index = section.getSectionIndex();
        int prevFlags = section.getRegion().getSectionFlags(index);

        int changes = this.updateSectionInfo(section, chunkBuildOutput.info);

        // if result was blocking (or is approximately visible) and section is now newly renderable, force render it since it's probably a newly uncovered chunk.
        // This also fixes flickering issues with pistons moving blocks and switching between being a mesh and a BE.
        if (this.renderTree != null &&
                (chunkBuildOutput.blockingTask || this.isSectionImmediatePresentationCandidate(viewport, section)) &&
                RenderSectionFlags.renderingMoreTypesNow(prevFlags, chunkBuildOutput.info.flags)) {
            int chunkX = section.getChunkX();
            int chunkY = section.getChunkY();
            int chunkZ = section.getChunkZ();

            for (SectionTree tree : this.cullResults.values()) {
                if (tree.patchMarkPresent(chunkX, chunkY, chunkZ)) {
                    changes |= SectionInfoChange.RENDER_LIST;
                }
            }

            gg.sona.radium.diag.Diag.count("patchPresent", "patchPresent section=" + chunkX + "," + chunkY + "," + chunkZ
                    + " blocking=" + chunkBuildOutput.blockingTask + " immediate=" + this.isSectionImmediatePresentationCandidate(viewport, section));

            // collect present patches if we need to
            if (pendingPresentPatches != null) {
                pendingPresentPatches.add(section);
            }
        }

        return changes;
    }

    private int updateSectionInfo(RenderSection render, BuiltSectionInfo info) {
        if (info == null || !RenderSectionFlags.needsRender(info.flags)) {
            this.renderableSectionTree.remove(render);
        } else {
            this.renderableSectionTree.add(render);
        }

        int changes = render.setInfo(info);

        boolean globalSetChanged;
        if (info == null || ArrayUtils.isEmpty(info.globalBlockEntities)) {
            globalSetChanged = this.sectionsWithGlobalEntities.remove(render);
        } else {
            globalSetChanged = this.sectionsWithGlobalEntities.add(render);
        }

        // invalidate render list when membership of global block entity set changes
        if (globalSetChanged) {
            changes |= SectionInfoChange.RENDER_LIST;
        }

        return changes;
    }

    private List<RenderSection> applyBuildOutputs(ArrayList<BuilderTaskOutput> outputs) {
        List<RenderSection> sectionsWithPendingOutputs = new ReferenceArrayList<RenderSection>();

        for (BuilderTaskOutput output : outputs) {
            // throw out outdated or duplicate outputs
            if (output.section.isDisposed()) {
                continue;
            }

            if (output.section.addBuildOutput(output)) {
                sectionsWithPendingOutputs.add(output.section);
            }
        }

        return sectionsWithPendingOutputs;
    }

    private ArrayList<BuilderTaskOutput> collectChunkBuildResults() {
        ArrayList<BuilderTaskOutput> results = new ArrayList<>();
        ChunkJobResult<? extends BuilderTaskOutput> result;

        while ((result = this.buildResults.poll()) != null) {
            results.add(result.unwrap());
            result.clearJobFromSection();

            JobEffort jobEffort = result.getJobEffort();
            if (jobEffort != null) {
                this.jobDurationEstimator.addData(jobEffort);
            }
        }

        this.jobDurationEstimator.updateModels();

        return results;
    }

    public void cleanupAndFlip() {
        this.sectionCache.cleanup();
        this.regions.update();
    }

    public void updateChunks(Viewport viewport, boolean updateImmediately) {
        this.thisFrameBlockingTasks = 0;
        this.nextFrameBlockingTasks = 0;
        this.deferredTasks = 0;

        ChunkJobCollector thisFrameBlockingCollector = this.lastBlockingCollector;
        this.lastBlockingCollector = null;
        if (thisFrameBlockingCollector == null) {
            thisFrameBlockingCollector = new ChunkJobCollector(this.buildResults::add);
        }

        if (updateImmediately) {
            // for a perfect frame where everything is finished use the last frame's blocking collector
            // and add all tasks to it so that they're waited on
            this.submitSectionTasks(thisFrameBlockingCollector, thisFrameBlockingCollector, thisFrameBlockingCollector, UnlimitedResourceBudget.INSTANCE, viewport);

            this.thisFrameBlockingTasks = thisFrameBlockingCollector.getSubmittedTaskCount();
            thisFrameBlockingCollector.awaitCompletion(this.builder);
        } else {
            long remainingDuration = this.builder.getTotalRemainingDuration(this.averageFrameDuration);

            // an estimator is used estimate task duration and limit the execution time to the available worker capacity.
            // separately, tasks are limited by their estimated upload size and duration.
            LimitedResourceBudget uploadBudget = new LimitedResourceBudget(
                    Math.max((long)(this.averageFrameDuration * FRAME_DURATION_UPLOAD_FRACTION), MIN_UPLOAD_DURATION_BUDGET),
                    this.regions.getStagingBuffer().getUploadSizeLimit(this.averageFrameDuration));

            ChunkJobCollector nextFrameBlockingCollector = new ChunkJobCollector(this.buildResults::add);
            ChunkJobCollector deferredCollector = new ChunkJobCollector(remainingDuration, this.buildResults::add);

            // if zero frame delay is allowed, submit important sorts with the current frame blocking collector.
            // otherwise submit with the collector that the next frame is blocking on.
            if (this.sortBehavior.getDeferMode() == DeferMode.ZERO_FRAMES) {
                this.submitSectionTasks(thisFrameBlockingCollector, nextFrameBlockingCollector, deferredCollector, uploadBudget, viewport);
            } else {
                this.submitSectionTasks(nextFrameBlockingCollector, nextFrameBlockingCollector, deferredCollector, uploadBudget, viewport);
            }

            this.thisFrameBlockingTasks = thisFrameBlockingCollector.getSubmittedTaskCount();
            this.nextFrameBlockingTasks = nextFrameBlockingCollector.getSubmittedTaskCount();
            this.deferredTasks = deferredCollector.getSubmittedTaskCount();

            // wait on this frame's blocking collector which contains the important tasks from this frame
            // and semi-important tasks from the last frame
            thisFrameBlockingCollector.awaitCompletion(this.builder);

            // store the semi-important collector to wait on it in the next frame
            this.lastBlockingCollector = nextFrameBlockingCollector;
        }
        // taskLists is null until the first cull result arrives (async culling), so guard it
        gg.sona.radium.diag.Diag.throttle("updateChunks", "updateChunks thisFrame=" + this.thisFrameBlockingTasks
                + " nextFrame=" + this.nextFrameBlockingTasks + " deferred=" + this.deferredTasks
                + " queue=" + (this.taskLists == null ? -1 : this.taskLists.size())
                + " builderQueued=" + this.builder.getScheduledJobCount());
    }

    private void submitSectionTasks(
            ChunkJobCollector importantCollector, ChunkJobCollector semiImportantCollector, ChunkJobCollector deferredCollector, UploadResourceBudget uploadBudget, Viewport viewport) {
        submitImportantSectionTasks(importantCollector, uploadBudget, DeferMode.ZERO_FRAMES, viewport);
        submitImportantSectionTasks(semiImportantCollector, uploadBudget, DeferMode.ONE_FRAME, viewport);
        submitImportantSectionTasks(deferredCollector, uploadBudget, DeferMode.ALWAYS, viewport);
        submitDeferredSectionTasks(deferredCollector, uploadBudget);
    }

    private void submitDeferredSectionTasks(ChunkJobCollector collector, UploadResourceBudget uploadBudget) {
        if (this.taskLists == null) {
            return;
        }

        while (!this.taskLists.isEmpty() && collector.hasBudgetRemaining() && uploadBudget.isAvailable()) {
            RenderSection section = this.sectionByPosition.get(this.taskLists.dequeueNextSectionPos());
            if (section != null) {
                submitSectionTask(collector, section, uploadBudget);
            }
        }
    }

    private DeferMode getDeferModeForPendingUpdate(int type) {
        return ChunkUpdateTypes.getDeferMode(type, SodiumClientMod.options().performance.chunkBuildDeferMode, this.sortBehavior.getDeferMode());

    }

    private void submitImportantSectionTasks(ChunkJobCollector collector, UploadResourceBudget uploadBudget, DeferMode deferMode, Viewport viewport) {
        Iterator<RenderSection> it = this.importantTasks.get(deferMode).iterator();

        while (it.hasNext() && collector.hasBudgetRemaining() && (deferMode.allowsUnlimitedUploadDuration() || uploadBudget.isAvailable())) {
            RenderSection section = it.next();
            int pendingUpdate = section.getPendingUpdate();

            if (pendingUpdate != 0 && this.getDeferModeForPendingUpdate(pendingUpdate) == deferMode && this.shouldPrioritizeTask(section, NEARBY_SORT_DISTANCE)) {
                // isSectionVisible includes a special case for not testing empty sections against the tree as they won't be in it
                if (this.renderTree == null || this.renderTree.isSectionVisible(viewport, section)) {
                    submitSectionTask(collector, section, pendingUpdate, uploadBudget, deferMode == DeferMode.ZERO_FRAMES);
                } else {
                    // don't remove if simply not visible currently but still relevant
                    continue;
                }
            }
            it.remove();
        }
    }

    private void submitSectionTask(ChunkJobCollector collector, @NotNull RenderSection section, UploadResourceBudget uploadBudget) {
        // don't schedule tasks for sections that don't need it anymore,
        // since the pending update it cleared when a task is started, this includes
        // sections for which there's a currently running task.
        int type = section.getPendingUpdate();
        if (type == 0) {
            return;
        }

        submitSectionTask(collector, section, type, uploadBudget, false);
    }


    private void submitSectionTask(ChunkJobCollector collector, @NotNull RenderSection section, int type, UploadResourceBudget uploadBudget, boolean blocking) {
        if (section.isDisposed()) {
            return;
        }

        ChunkBuilderTask<? extends BuilderTaskOutput> task;
        if (ChunkUpdateTypes.isInitialBuild(type) || ChunkUpdateTypes.isRebuild(type)) {
            task = this.createRebuildTask(section, this.frame, blocking);
            if (task == null) {
                // if the section is empty or doesn't exist submit this null-task to set the
                // built flag on the render section.
                // It's important to use a NoData instead of null translucency data here in
                // order for it to clear the old data from the translucency sorting system.
                // This doesn't apply to sorting tasks as that would result in the section being
                // marked as empty just because it was scheduled to be sorted and its dynamic
                // data has since been removed. In that case simply nothing is done as the
                // rebuild that must have happened in the meantime includes new non-dynamic
                // index data.
                TranslucentData translucentData = null;
                if (this.sortBehavior != SortBehavior.OFF) {
                    translucentData = NoData.forEmptySection(section.getPosition());
                }
                ChunkJobResult<ChunkBuildOutput> result = ChunkJobResult.successfully(new ChunkBuildOutput(
                        section, this.frame, translucentData,
                        BuiltSectionInfo.EMPTY, Collections.emptyMap(), false));
                this.buildResults.add(result);
            }
        } else { // implies it's a type of sort task
            task = this.createSortTask(section, this.frame);

            if (task == null) {
                // when a sort task is null it means the render section has no dynamic data and
                // doesn't need to be sorted. Nothing needs to be done.
                section.clearPendingUpdate();
                return;
            }
        }

        if (task != null) {
            ChunkJob job = this.builder.scheduleTask(task, ChunkUpdateTypes.isImportant(type), collector::onJobFinished);
            collector.addSubmittedJob(job);

            // consume upload budget in size and duration using estimates
            uploadBudget.consume(task.getEstimatedUploadDuration(), task.getEstimatedSize());

            section.addRunningJob(job);
        }

        section.clearPendingUpdate();
    }

    public @Nullable ChunkBuilderMeshingTask createRebuildTask(RenderSection render, int frame, boolean blocking) {
        ChunkRenderContext context = LevelSlice.prepare(this.level, render.getPosition(), this.sectionCache);

        if (context == null) {
            return null;
        }

        ChunkBuilderMeshingTask task = new ChunkBuilderMeshingTask(render, frame, this.cameraPosition, context, this.sortBehavior, ChunkUpdateTypes.isRebuildWithSort(render.getPendingUpdate()), blocking);
        task.calculateEstimations(this.jobDurationEstimator, this.meshTaskSizeEstimator, this.jobUploadDurationEstimator);
        return task;
    }

    public ChunkBuilderSortingTask createSortTask(RenderSection render, int frame) {
        ChunkBuilderSortingTask task = ChunkBuilderSortingTask.createTask(render, frame, this.cameraPosition);
        if (task != null) {
            task.calculateEstimations(this.jobDurationEstimator, this.meshTaskSizeEstimator, this.jobUploadDurationEstimator);
        }
        return task;
    }

    public void processGFNIMovement(CameraMovement movement) {
        if (this.sortTriggering != null) {
            this.sortTriggering.triggerSections(this::scheduleSort, movement);
        }
    }

    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    private static final class SyncLocalTreeCollector implements OcclusionCuller.VisibilityTestingVisitor {
        private final RayOcclusionSectionTree tree;
        private final SyncVisibleChunkCollector collector;

        private SyncLocalTreeCollector(RayOcclusionSectionTree tree, SyncVisibleChunkCollector collector) {
            this.tree = tree;
            this.collector = collector;
        }

        @Override
        public boolean visitTestVisible(RenderSection section) {
            if (!this.tree.visitTestVisible(section)) {
                return false;
            }

            this.collector.visit(section);
            return true;
        }

        @Override
        public void visit(RenderSection section, boolean inFrustum) {
            this.tree.visit(section, inFrustum);
            this.collector.visit(section);
        }
    }

    private static final class SyncVisibleChunkCollector implements RenderListProvider {
        private static int[] sortItems = new int[RenderRegion.REGION_SIZE];

        private final int frame;
        private final ObjectArrayList<ChunkRenderList> renderLists = new ObjectArrayList<>();

        private SyncVisibleChunkCollector(int frame) {
            this.frame = frame;
        }

        public void visit(RenderSection section) {
            if (!section.needsRender()) {
                return;
            }

            RenderRegion region = section.getRegion();
            ChunkRenderList renderList = region.getRenderList();

            if (renderList.getLastVisibleFrame() != this.frame) {
                renderList.reset(this.frame);
                this.renderLists.add(renderList);
            }

            renderList.add(section.getSectionIndex());
        }

        @Override
        public ObjectArrayList<ChunkRenderList> getUnsortedRenderLists() {
            return this.renderLists;
        }

        @Override
        public int[] getCachedSortItems() {
            return sortItems;
        }

        @Override
        public void setCachedSortItems(int[] sortItems) {
            SyncVisibleChunkCollector.sortItems = sortItems;
        }
    }

    public void destroy() {
        this.builder.shutdown(); // stop all the workers, and cancel any tasks

        this.asyncCullExecutor.shutdownNow();

        for (BuilderTaskOutput result : this.collectChunkBuildResults()) {
            result.destroy(); // delete resources for any pending tasks (including those that were cancelled)
        }

        for (RenderSection section : this.sectionByPosition.values()) {
            section.delete();
        }

        this.buildResults.clear();
        this.sectionByPosition.clear();
        this.sectionsWithGlobalEntities.clear();
        this.cullResults.clear();
        this.importantTasks.values().forEach(Collection::clear);
        this.sectionCache.clear();
        this.renderableSectionTree.clear();
        this.renderLists = SortedRenderLists.empty();
        this.taskLists = null;
        this.pendingTask = null;
        this.renderTree = null;
        this.cameraPosition = null;
        this.lastBlockingCollector = null;

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.regions.delete(commandList);
            this.chunkRenderer.delete(commandList);
        }
    }

    public int getTotalSections() {
        return this.sectionByPosition.size();
    }

    public int getVisibleChunkCount() {
        int sections = 0;
        Iterator<ChunkRenderList> iterator = this.renderLists.iterator();

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();
            sections += renderList.getSectionsWithGeometryCount();
        }

        return sections;
    }

    private boolean upgradePendingUpdate(RenderSection section, int updateType) {
        if (updateType == 0) {
            return false;
        }

        int current = section.getPendingUpdate();
        int joined = ChunkUpdateTypes.join(current, updateType);

        if (joined == current) {
            return false;
        }

        section.setPendingUpdate(joined, this.lastFrameAtTime);

        // when the pending task type changes, and it's important, add it to the list of important tasks
        if (ChunkUpdateTypes.isImportant(joined)) {
            this.importantTasks.get(this.getDeferModeForPendingUpdate(joined)).add(section);
        }

        // mark graph as dirty so that it picks up the section's pending task
        this.markGraphDirty();

        return true;
    }

    public void scheduleSort(long sectionPos, boolean isDirectTrigger) {
        RenderSection section = this.sectionByPosition.get(sectionPos);

        if (section != null) {
            int pendingUpdate = ChunkUpdateTypes.SORT;
            SortBehavior.PriorityMode priorityMode = this.sortBehavior.getPriorityMode();
            if (priorityMode == PriorityMode.NEARBY && this.shouldPrioritizeTask(section, NEARBY_SORT_DISTANCE) || priorityMode == PriorityMode.ALL) {
                pendingUpdate = ChunkUpdateTypes.join(pendingUpdate, ChunkUpdateTypes.IMPORTANT);
            }
            if (this.upgradePendingUpdate(section, pendingUpdate)) {
                section.prepareTrigger(isDirectTrigger);
            }
        }
    }

    public void scheduleRebuild(int x, int y, int z, boolean playerChanged) {
        this.sectionCache.invalidate(x, y, z);

        RenderSection section = this.sectionByPosition.get(SectionPos.asLong(x, y, z));

        if (section != null && section.isBuilt()) {
            int pendingUpdate;

            if (playerChanged && this.shouldPrioritizeTask(section, NEARBY_REBUILD_DISTANCE)) {
                pendingUpdate = ChunkUpdateTypes.join(ChunkUpdateTypes.REBUILD, ChunkUpdateTypes.IMPORTANT);
            } else {
                pendingUpdate = ChunkUpdateTypes.join(ChunkUpdateTypes.REBUILD, ChunkUpdateTypes.IMPORTANT);
            }

            this.upgradePendingUpdate(section, pendingUpdate);
        }
    }

    private boolean shouldPrioritizeTask(RenderSection section, float distance) {
        return this.cameraPosition != null && section.getSquaredDistance(
                (float) this.cameraPosition.x(),
                (float) this.cameraPosition.y(),
                (float) this.cameraPosition.z()
        ) < distance;
    }

    private float getEffectiveRenderDistance() {
        float alpha = 1f;
        float distance = FogHelper.getFogEnd();

        float renderDistance = this.getRenderDistance();

        // The fog must be fully opaque in order to skip rendering of chunks behind it
        if (!Mth.equal(alpha, 1.0f)) {
            return renderDistance;
        }

        return Math.min(renderDistance, distance + 0.5f);
    }

    private float getRenderDistance() {
        return this.renderDistance * 16.0f;
    }

    private void connectNeighborNodes(RenderSection render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            RenderSection adj = this.getRenderSection(render.getChunkX() + GraphDirection.x(direction),
                    render.getChunkY() + GraphDirection.y(direction),
                    render.getChunkZ() + GraphDirection.z(direction));

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), render);
                render.setAdjacentNode(direction, adj);
            }
        }
    }

    private void disconnectNeighborNodes(RenderSection render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            RenderSection adj = render.getAdjacent(direction);

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), null);
                render.setAdjacentNode(direction, null);
            }
        }
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sectionByPosition.get(SectionPos.asLong(x, y, z));
    }

    public Collection<String> getDebugStrings() {
        List<String> list = new ArrayList<>();

        int count = 0;

        long geometryDeviceUsed = 0;
        long geometryDeviceAllocated = 0;
        long indexDeviceUsed = 0;
        long indexDeviceAllocated = 0;

        for (RenderRegion region : this.regions.getLoadedRegions()) {
            RenderRegion.DeviceResources resources = region.getResources();

            if (resources == null) {
                continue;
            }

            GlBufferArena geometryArena = resources.getGeometryArena();
            geometryDeviceUsed += geometryArena.getDeviceUsedMemory();
            geometryDeviceAllocated += geometryArena.getDeviceAllocatedMemory();

            GlBufferArena indexArena = resources.getIndexArena();
            indexDeviceUsed += indexArena.getDeviceUsedMemory();
            indexDeviceAllocated += indexArena.getDeviceAllocatedMemory();

            count++;
        }

        list.add(String.format("Pools: Geometry %d/%d MiB, Index %d/%d MiB (%d buffers)",
                MathUtil.toMib(geometryDeviceUsed), MathUtil.toMib(geometryDeviceAllocated),
                MathUtil.toMib(indexDeviceUsed), MathUtil.toMib(indexDeviceAllocated), count));
        list.add(String.format("Transfer Queue: %s", this.regions.getStagingBuffer().toString()));

        list.add(String.format("Chunk Builder: Schd=%02d | Busy=%02d (%04d%%) | Total=%02d",
                this.builder.getScheduledJobCount(), this.builder.getBusyThreadCount(), (int) (this.builder.getBusyFraction(this.lastFrameDuration) * 100), this.builder.getTotalThreadCount()));

        list.add(String.format("Tasks: N0=%03d | N1=%03d | Def=%03d, Recv=%03d",
                this.thisFrameBlockingTasks, this.nextFrameBlockingTasks, this.deferredTasks, this.buildResults.size())
        );

        if (PlatformRuntimeInformation.getInstance().isDevelopmentEnvironment()) {
            String meshTaskParameters = this.jobDurationEstimator.toString(ChunkBuilderMeshingTask.class);
            String sortTaskParameters = this.jobDurationEstimator.toString(ChunkBuilderSortingTask.class);
            String uploadDurationParameters = this.jobUploadDurationEstimator.toString(null);
            list.add(String.format("Duration: Mesh %s, Sort %s, Upload %s", meshTaskParameters, sortTaskParameters, uploadDurationParameters));

            ReferenceArrayList<String> sizeEstimates = new ReferenceArrayList<String>();
            for (MeshResultSize.SectionCategory type : MeshResultSize.SectionCategory.values()) {
                sizeEstimates.add(String.format("%s=%s", type, this.meshTaskSizeEstimator.toString(type)));
            }
            list.add(String.format("Size: %s", String.join(", ", sizeEstimates.toArray(new String[0]))));
        }

        if (this.sortBehavior != SortBehavior.OFF) {
            this.sortTriggering.addDebugStrings(list, this.sortBehavior);
        } else {
            list.add("TS OFF");
        }

        return list;
    }

    public @NotNull SortedRenderLists getRenderLists() {
        return this.renderLists;
    }

    public boolean isSectionBuilt(int x, int y, int z) {
        RenderSection section = this.getRenderSection(x, y, z);
        return section != null && section.isBuilt();
    }

    public void onChunkAdded(int x, int z) {
        for (int y = 0; y < 16; y++) {
            this.onSectionAdded(x, y, z);
        }
    }

    public void onChunkRemoved(int x, int z) {
        for (int y = 0; y < 16; y++) {
            this.onSectionRemoved(x, y, z);
        }
    }

    public Collection<RenderSection> getSectionsWithGlobalEntities() {
        return ReferenceSets.unmodifiable(this.sectionsWithGlobalEntities);
    }
}

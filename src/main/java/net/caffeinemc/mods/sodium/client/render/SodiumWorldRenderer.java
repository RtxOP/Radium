package net.caffeinemc.mods.sodium.client.render;

import dev.vexor.radium.compat.mojang.minecraft.render.FogHelper;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Provides an extension to vanilla's {@link net.minecraft.client.renderer.RenderGlobal}.
 *
 * <p>MCP 1.8.9 divergences from the reference (which targets legacy-yarn's patched client):</p>
 * <ul>
 *     <li>The legacy-yarn {@code Camera} class (static PROJECTION/MODEL matrices, rotation getters) does not
 *     exist under MCP. The faithful equivalent is a readback of the current GL matrices: vanilla 1.8.9 configures
 *     both matrices in {@code EntityRenderer.setupCameraTransform()} before {@code RenderGlobal.setupTerrain()}
 *     and the block-layer passes, so {@link #captureGlMatrices()} returns exactly the matrices the vanilla
 *     renderer would have drawn with.</li>
 *     <li>{@code GameOptions.viewDistance} is {@code GameSettings.renderDistanceChunks}.</li>
 *     <li>{@code RenderLayer} is {@code EnumWorldBlockLayer} ({@link DefaultTerrainRenderPasses#fromLayer}).</li>
 *     <li>{@code BlockBreakingInfo} is {@code DestroyBlockProgress}.</li>
 *     <li>{@code BlockEntity} is {@code TileEntity} ({@code TileEntityRendererDispatcher.instance}).</li>
 *     <li>{@code Entity.shouldRenderName()} is {@code Entity.getAlwaysRenderNameTagForRender()}.</li>
 * </ul>
 */
public class SodiumWorldRenderer {
    private final Minecraft client;

    private WorldClient level;
    private int renderDistance;

    private Vector3d lastCameraPos;
    private double lastCameraPitch, lastCameraYaw;
    private float lastFogDistance;
    private Matrix4f lastProjectionMatrix;

    private boolean useEntityCulling;

    private RenderSectionManager renderSectionManager;

    /**
     * @return The SodiumWorldRenderer based on the current dimension
     */
    public static SodiumWorldRenderer instance() {
        SodiumWorldRenderer instance = instanceNullable();

        if (instance == null) {
            throw new IllegalStateException("No renderer attached to active level");
        }

        return instance;
    }

    /**
     * @return The SodiumWorldRenderer based on the current dimension, or null if none is attached
     */
    public static SodiumWorldRenderer instanceNullable() {
        if (Minecraft.getMinecraft().renderGlobal instanceof LevelRendererExtension) {
            return ((LevelRendererExtension) Minecraft.getMinecraft().renderGlobal).sodium$getWorldRenderer();
        }

        return null;
    }

    /**
     * Reads the current projection/model-view matrices from the fixed-function GL state. MCP 1.8.9 has no
     * {@code Camera} class; vanilla populates these GL matrices every frame in
     * {@code EntityRenderer.setupCameraTransform()} prior to the terrain passes, so the readback is
     * semantically identical to the reference's {@code Camera.PROJECTION_MATRIX}/{@code Camera.MODEL_MATRIX}.
     *
     * <p>Note: 1.8.9 applies head-bob/hurt-tilt translations as part of the camera transform, so the model-view
     * matrix may contain a small translation component. This matches what vanilla itself would have used for
     * chunk rendering (the reference's patched camera does the same), so no normalization is applied.</p>
     */
    public static ChunkRenderMatrices captureGlMatrices() {
        Matrix4f projection = readGlMatrix(GL11.GL_PROJECTION_MATRIX);
        Matrix4f modelView = readGlMatrix(GL11.GL_MODELVIEW_MATRIX);
        // Zero out translation to prevent double-applying camera eye translation with u_RegionOffset
        modelView.setTranslation(0.0f, 0.0f, 0.0f);
        return new ChunkRenderMatrices(projection, modelView);
    }

    private static Matrix4f readGlMatrix(int matrixMode) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(matrixMode, buffer);
        // joml's FloatBuffer constructor reads the buffer in row-major naming order (buffer[0]->m00, buffer[1]->m01,
        // ...), so GL's column-major matrix lands TRANSPOSED here. This is fine: the shader upload (GlUniformMatrix4f:
        // get(buffer) + glUniformMatrix4(transpose=false)) transposes it back, so GLSL sees the true GL matrices.
        return new Matrix4f(buffer);
    }

    public SodiumWorldRenderer(Minecraft client) {
        this.client = client;
    }

    public void setLevel(WorldClient level) {
        // Check that the level is actually changing
        if (this.level == level) {
            return;
        }

        // If we have a level is already loaded, unload the renderer
        if (this.level != null) {
            this.unloadLevel();
        }

        // If we're loading a new level, load the renderer
        if (level != null) {
            this.loadLevel(level);
        }
    }

    private void loadLevel(WorldClient level) {
        this.level = level;

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    private void unloadLevel() {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.level = null;
    }

    /**
     * @return The number of chunk renders which are visible in the current camera's frustum
     */
    public int getVisibleChunkCount() {
        return this.renderSectionManager.getVisibleChunkCount();
    }

    /**
     * Notifies the chunk renderer that the graph scene has changed and should be re-computed.
     */
    public void scheduleTerrainUpdate() {
        // BUG: seems to be called before init
        if (this.renderSectionManager != null) {
            this.renderSectionManager.markGraphDirty();
        }
    }

    /**
     * @return True if no chunks are pending rebuilds
     */
    public boolean isTerrainRenderComplete() {
        return this.renderSectionManager.getBuilder().isBuildQueueEmpty();
    }

    /**
     * Called prior to any chunk rendering in order to update necessary state.
     */
    public void setupTerrain(Viewport viewport,
                             boolean spectator,
                             boolean updateChunksImmediately) {
        NativeBuffer.reclaim(false);

        this.processChunkEvents();

        this.useEntityCulling = SodiumClientMod.options().performance.useEntityCulling;

        if (this.client.gameSettings.renderDistanceChunks != this.renderDistance) {
            this.reload();
        }

        Profiler profiler = this.client.mcProfiler;
        profiler.startSection("camera_setup");

        EntityPlayerSP player = this.client.thePlayer;

        if (player == null) {
            throw new IllegalStateException("Client instance has no active player entity");
        }

        CameraTransform cam = viewport.getTransform();
        Vector3d pos = new Vector3d(cam.x, cam.y, cam.z);
        Matrix4f projectionMatrix = readGlMatrix(GL11.GL_PROJECTION_MATRIX);

        Entity cameraEntity = this.client.getRenderViewEntity();
        if (cameraEntity == null) {
            cameraEntity = player;
        }

        float pitch = cameraEntity.rotationPitch;
        float yaw = cameraEntity.rotationYaw;
        float fogDistance = FogHelper.getFogEnd();

        if (this.lastCameraPos == null) {
            this.lastCameraPos = pos;
        }
        if (this.lastProjectionMatrix == null) {
            this.lastProjectionMatrix = new Matrix4f(projectionMatrix);
        }
        boolean cameraLocationChanged = !pos.equals(this.lastCameraPos);
        boolean cameraAngleChanged = pitch != this.lastCameraPitch || yaw != this.lastCameraYaw || fogDistance != this.lastFogDistance;
        boolean cameraProjectionChanged = !projectionMatrix.equals(this.lastProjectionMatrix, 0.0001f);

        this.lastProjectionMatrix = projectionMatrix;

        this.lastCameraPitch = pitch;
        this.lastCameraYaw = yaw;

        if (cameraLocationChanged || cameraAngleChanged || cameraProjectionChanged) {
            this.renderSectionManager.notifyChangedCamera();
        }

        this.lastFogDistance = fogDistance;

        this.renderSectionManager.prepareFrame(pos);

        gg.sona.radium.diag.Diag.throttle("cameraFlags", "cameraFlags moved=" + cameraLocationChanged
                + " angle=" + cameraAngleChanged + " proj=" + cameraProjectionChanged
                + " fogEnd=" + String.format("%.1f", fogDistance));

        if (cameraLocationChanged) {
            profiler.endStartSection("translucent_triggering");

            this.renderSectionManager.processGFNIMovement(new CameraMovement(this.lastCameraPos, pos));
            this.lastCameraPos = pos;
        }

        int maxChunkUpdates = updateChunksImmediately ? this.renderDistance : 1;

        for (int i = 0; i < maxChunkUpdates; i++) {
            long t0 = System.nanoTime();
            this.renderSectionManager.prepareRender();
            long t1 = System.nanoTime();

            profiler.endStartSection("chunk_render_lists");

            this.renderSectionManager.prepareRenderTrees(viewport, spectator);
            long t2 = System.nanoTime();

            profiler.endStartSection("chunk_update");

            this.renderSectionManager.cleanupAndFlip();
            this.renderSectionManager.updateChunks(viewport, updateChunksImmediately);
            long t3 = System.nanoTime();

            profiler.endStartSection("chunk_upload");

            this.renderSectionManager.processChunkBuilds(viewport);
            long t4 = System.nanoTime();

            gg.sona.radium.diag.Diag.throttle("setupTimings", "setupTimings prepare=" + ((t1 - t0) / 1000000)
                    + "ms trees=" + ((t2 - t1) / 1000000) + "ms update=" + ((t3 - t2) / 1000000)
                    + "ms upload=" + ((t4 - t3) / 1000000) + "ms");
            gg.sona.radium.diag.Diag.glProbe("setupTerrain");

            if (!this.renderSectionManager.needsUpdate()) {
                break;
            }
        }

        profiler.endStartSection("chunk_render_lists");

        this.renderSectionManager.finalizeRenderLists(viewport, updateChunksImmediately);

        profiler.endStartSection("chunk_render_tick");

        this.renderSectionManager.tickVisibleRenders();

        profiler.endSection();

        gg.sona.radium.diag.Diag.glProbe("postSetupTerrain");
    }

    private void processChunkEvents() {
        this.renderSectionManager.beforeSectionUpdates();
        ChunkTracker tracker = ChunkTrackerHolder.get(this.level);
        tracker.forEachEvent(this.renderSectionManager::onChunkAdded, this.renderSectionManager::onChunkRemoved);
    }

    /**
     * Performs a render pass for the given {@link EnumWorldBlockLayer} and draws all visible chunks for it.
     */
    public void drawChunkLayer(EnumWorldBlockLayer renderLayer, ChunkRenderMatrices matrices, double x, double y, double z) {
        this.renderSectionManager.renderLayer(matrices, DefaultTerrainRenderPasses.fromLayer(renderLayer), x, y, z);
    }

    public void reload() {
        if (this.level == null) {
            return;
        }

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    private void initRenderer(CommandList commandList) {
        if (this.renderSectionManager != null) {
            this.renderSectionManager = null;
        }

        // translucency sorting can be disabled in development environments by setting the debug option in the config file
        SortBehavior sortBehavior = SortBehavior.DYNAMIC_DEFER_NEARBY_ZERO_FRAMES;

        // (Port divergence: the reference also checks SodiumClientMod.options().debug.terrainSortingEnabled;
        // our SodiumOptions has no debug section, so the development-environment check is dropped.)
        if (PlatformRuntimeInformation.getInstance().isDevelopmentEnvironment()) {
            sortBehavior = SortBehavior.OFF;
        }

        this.renderDistance = this.client.gameSettings.renderDistanceChunks;

        this.renderSectionManager = new RenderSectionManager(this.level, this.renderDistance, sortBehavior, commandList);

        ChunkTracker tracker = ChunkTrackerHolder.get(this.level);
        ChunkTracker.forEachChunk(tracker.getReadyChunks(), this.renderSectionManager::onChunkAdded);
    }

    public void renderBlockEntities(Map<Integer, DestroyBlockProgress> blockBreakingProgressions, float tickDelta) {
        TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;

        this.renderGlobalBlockEntities(tickDelta, dispatcher);
        this.renderBlockEntities(blockBreakingProgressions, tickDelta, dispatcher);
    }

    private void renderBlockEntities(Map<Integer, DestroyBlockProgress> blockBreakingProgressions,
                                     float tickDelta,
                                     TileEntityRendererDispatcher dispatcher) {
        SortedRenderLists renderLists = this.renderSectionManager.getRenderLists();
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            ChunkRenderList renderList = renderListIterator.next();

            RenderRegion renderRegion = renderList.getRegion();
            ByteIterator renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                int renderSectionId = renderSectionIterator.nextByteAsInt();

                TileEntity[] blockEntities = renderRegion.getCulledBlockEntities(renderSectionId);

                if (blockEntities == null) {
                    continue;
                }

                for (TileEntity blockEntity : blockEntities) {
                    dispatcher.renderTileEntity(blockEntity, tickDelta, -1);
                }
            }

            for (DestroyBlockProgress info : blockBreakingProgressions.values()) {
                BlockPos blockPos = info.getPosition();
                TileEntity blockEntity = this.level.getTileEntity(blockPos);

                if (blockEntity instanceof TileEntityChest) {
                    TileEntityChest chest = (TileEntityChest) blockEntity;
                    if (chest.adjacentChestXNeg != null) {
                        blockPos = blockPos.offset(EnumFacing.WEST);
                        blockEntity = this.level.getTileEntity(blockPos);
                    } else if (chest.adjacentChestZNeg != null) {
                        blockPos = blockPos.offset(EnumFacing.NORTH);
                        blockEntity = this.level.getTileEntity(blockPos);
                    }
                }

                if (blockEntity == null) {
                    continue;
                }

                dispatcher.renderTileEntity(blockEntity, tickDelta, info.getPartialBlockDamage());
            }
        }
    }

    private void renderGlobalBlockEntities(float tickDelta, TileEntityRendererDispatcher dispatcher) {
        for (RenderSection renderSection : this.renderSectionManager.getSectionsWithGlobalEntities()) {
            TileEntity[] blockEntities = renderSection.getRegion().getGlobalBlockEntities(renderSection.getSectionIndex());

            if (blockEntities == null) {
                continue;
            }

            for (TileEntity blockEntity : blockEntities) {
                dispatcher.renderTileEntity(blockEntity, tickDelta, -1);
            }
        }
    }

    // the volume of a section multiplied by the number of sections to be checked at most
    private static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 50;

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     * @return True if the entity is visible, otherwise false
     */
    public <T extends Entity> boolean isEntityVisible(T entity) {
        if (!this.useEntityCulling) {
            return true;
        }

        // Ensure entities with outlines or nametags are always visible
        if (entity.getAlwaysRenderNameTagForRender()) {
            return true;
        }

        AxisAlignedBB bb = entity.getEntityBoundingBox();

        // bail on very large entities to avoid checking many sections
        double entityVolume = (bb.maxX - bb.minX) * (bb.maxY - bb.minY) * (bb.maxZ - bb.minZ);
        if (entityVolume > MAX_ENTITY_CHECK_VOLUME) {
            // large entities are only frustum tested, their sections are not checked for visibility
            return true;
        }

        return this.isBoxVisible(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        return this.renderSectionManager.isBoxVisible(x1, y1, z1, x2, y2, z2);
    }

    public String getChunksDebugString() {
        // C: visible/total D: distance
        // TODO: add dirty and queued counts
        return String.format("C: %d/%d D: %d", this.renderSectionManager.getVisibleChunkCount(), this.renderSectionManager.getTotalSections(), this.renderDistance);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified block region.
     */
    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.scheduleRebuildForChunks(minX >> 4, minY >> 4, minZ >> 4, maxX >> 4, maxY >> 4, maxZ >> 4, important);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified chunk region.
     */
    public void scheduleRebuildForChunks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    this.scheduleRebuildForChunk(chunkX, chunkY, chunkZ, important);
                }
            }
        }
    }

    /**
     * Schedules a chunk rebuild for the render belonging to the given chunk section position.
     */
    public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
        this.renderSectionManager.scheduleRebuild(x, y, z, important);
    }

    public Collection<String> getDebugStrings() {
        return this.renderSectionManager.getDebugStrings();
    }

    public boolean isSectionReady(int x, int y, int z) {
        return this.renderSectionManager.isSectionBuilt(x, y, z);
    }
}

package gg.sona.radium.mixin.sodium.core.render.world;

import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.SimpleFrustum;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(RenderGlobal.class)
public abstract class LevelRendererMixin implements LevelRendererExtension {
    @Shadow
    @Final
    private Map<Integer, DestroyBlockProgress> damagedBlocks;
    @Shadow
    @Final
    private Minecraft mc;
    @Shadow
    private int countEntitiesRendered;
    @Shadow
    @Final
    private RenderManager renderManager;
    @Shadow
    private WorldClient theWorld;
    @Shadow
    private int countEntitiesTotal;
    @Unique
    private SodiumWorldRenderer renderer;

    @Override
    public SodiumWorldRenderer sodium$getWorldRenderer() {
        return this.renderer;
    }

    /**
     * @reason Do not allow any resources to be allocated for vanilla's unused chunk storage. Sodium owns
     * terrain rendering (renderBlockLayer/setupTerrain/updateChunks are overwritten), so the vanilla
     * ViewFrustum/RenderChunk arrays are pure memory waste. This mirrors the reference's redirect on
     * GameOptions.viewDistance (ordinal 1) inside its reload() equivalent.
     * @author JellySquid
     */
    @Redirect(method = "loadRenderers()V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;field_151451_c:I", ordinal = 1, remap = false))
    private int nullifyBuiltChunkStorage(GameSettings instance) {
        // Return a zero render distance: vanilla allocates a 1x1x16-column ViewFrustum (16 RenderChunks)
        // instead of (2d+1)^2*16 chunks. Nothing ever draws from it.
        return 0;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft mc, CallbackInfo ci) {
        this.renderer = new SodiumWorldRenderer(mc);
    }

    @Inject(method = "setWorldAndLoadRenderers", at = @At("RETURN"))
    private void onWorldChanged(WorldClient level, CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setLevel(level);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void onReload(IResourceManager resourceManager, CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @reason Redirect the chunk layer render passes to our renderer
     * @author JellySquid
     */
    @Overwrite
    public int renderBlockLayer(EnumWorldBlockLayer blockLayer, double tickDelta, int anaglyphFilter, Entity entity) {
        RenderDevice.enterManagedCode();

        double x = entity.prevPosX + (entity.posX - entity.prevPosX) * tickDelta;
        double y = entity.prevPosY + (entity.posY - entity.prevPosY) * tickDelta;
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * tickDelta;

        // MCP 1.8.9 has no Camera class (that is a legacy-yarn artefact); vanilla has already configured the
        // fixed-function projection/model-view matrices in EntityRenderer.setupCameraTransform(), so read them
        // back -- semantically identical to the reference's Camera.PROJECTION_MATRIX / Camera.MODEL_MATRIX.
        ChunkRenderMatrices matrices = SodiumWorldRenderer.captureGlMatrices();

        if (blockLayer == EnumWorldBlockLayer.SOLID) {
            gg.sona.radium.diag.Diag.worldSegmentStart();
        } else if (blockLayer == EnumWorldBlockLayer.TRANSLUCENT) {
            gg.sona.radium.diag.Diag.worldSegmentEnd();
        }

        this.mc.entityRenderer.enableLightmap();
        this.mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

        try {
            this.renderer.drawChunkLayer(blockLayer, matrices, x, y, z);
        } finally {
            RenderDevice.exitManagedCode();
        }
        this.mc.entityRenderer.disableLightmap();

        if (blockLayer == EnumWorldBlockLayer.TRANSLUCENT) {
            gg.sona.radium.diag.Diag.glStateProbe("afterTranslucent");
        }

        return 0;
    }

    /**
     * @reason Redirect the terrain setup phase to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setupTerrain(Entity entity, double tickDelta, ICamera camera, int frame, boolean spectator) {
        // The reference derives its frustum from the camera's projection/model matrices. 1.8.9 has no Camera
        // class; vanilla configures the fixed-function GL matrices in EntityRenderer.setupCameraTransform() before
        // setupTerrain runs, so read them back (translation zeroed, matching captureGlMatrices). Vanilla's own
        // ClippingHelper notch-extracts planes from the transposed matrix product (M*P), yielding a degenerate
        // frustum; building the FrustumIntersection from the combined P*M matrix (the exact clip transform the
        // block shader applies) produces the correct one.
        ChunkRenderMatrices matrices = SodiumWorldRenderer.captureGlMatrices();
        Matrix4f combined = new Matrix4f(matrices.projection).mul(matrices.modelView);
        SimpleFrustum frustum = new SimpleFrustum(new FrustumIntersection(combined, false));
        Vec3 transform = entity.getPositionEyes((float) tickDelta);
        Viewport viewport = new Viewport(frustum, transform);
        boolean updateChunksImmediately = false;

        gg.sona.radium.diag.Diag.cameraProbe("camera pos=(" + String.format("%.1f,%.1f,%.1f", transform.xCoord, transform.yCoord, transform.zCoord)
                + ") yaw=" + String.format("%.1f", entity.rotationYaw) + " pitch=" + String.format("%.1f", entity.rotationPitch)
                + " chunk=(" + (entity.chunkCoordX) + "," + (entity.chunkCoordY) + "," + (entity.chunkCoordZ) + ")"
                + " rd=" + this.mc.gameSettings.renderDistanceChunks
                + " fogEnd=" + dev.vexor.radium.compat.mojang.minecraft.render.FogHelper.getFogEnd()
                + " fogStart=" + dev.vexor.radium.compat.mojang.minecraft.render.FogHelper.getFogStart());

        RenderDevice.enterManagedCode();

        try {
            this.renderer.setupTerrain(viewport, spectator, updateChunksImmediately);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void markBlockRangeForRenderUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, true);
    }

    /**
     * @reason Redirect the updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void updateChunks(long finishTimeNano) {
    }

    /**
     * @author Decencies
     * @reason Redirect entities to our renderer
     */
    @Overwrite
    public void renderEntities(Entity player, ICamera camera, float partialTicks) {
        this.theWorld.theProfiler.startSection("prepare");
        Entity renderView = this.mc.getRenderViewEntity();

        TileEntityRendererDispatcher.instance
                .cacheActiveRenderInfo(this.theWorld, this.mc.getTextureManager(), this.mc.fontRendererObj, renderView, partialTicks);

        this.renderManager
                .cacheActiveRenderInfo(this.theWorld, this.mc.fontRendererObj, renderView, this.mc.pointedEntity, this.mc.gameSettings, partialTicks);

        double renderX = renderView.prevPosX + (renderView.posX - renderView.prevPosX) * partialTicks;
        double renderY = renderView.prevPosY + (renderView.posY - renderView.prevPosY) * partialTicks;
        double renderZ = renderView.prevPosZ + (renderView.posZ - renderView.prevPosZ) * partialTicks;
        TileEntityRendererDispatcher.staticPlayerX = renderX;
        TileEntityRendererDispatcher.staticPlayerY = renderY;
        TileEntityRendererDispatcher.staticPlayerZ = renderZ;

        this.renderManager.setRenderPosition(renderX, renderY, renderZ);
        this.mc.entityRenderer.enableLightmap();
        this.theWorld.theProfiler.endStartSection("global");
        List<Entity> list = this.theWorld.loadedEntityList;
        this.countEntitiesTotal = list.size();

        // Port fix (head-interior + double entity render): the reference's first loop iterates yarn
        // World.entities, which is MCP 1.8.9 WorldClient.weatherEffects (the weather/effect entity list),
        // NOT loadedEntityList. Vanilla 1.8.9's own renderEntities first loop does the same. Iterating
        // loadedEntityList rendered EVERY in-range entity twice (including the local player, which put
        // the camera inside the player model in first person) and doubled entity render cost.
        Entity effect;
        for (int j = 0; j < this.theWorld.weatherEffects.size(); ++j) {
            effect = this.theWorld.weatherEffects.get(j);
            if (effect.isInRangeToRender3d(renderX, renderY, renderZ)) {
                this.renderManager.renderEntitySimple(effect, partialTicks);
            }
        }

        // Apply entity distance scaling
        for (Entity entity : this.theWorld.loadedEntityList) {
            // Skip entities that shouldn't render in this pass
            //if(!entity.shouldRenderInPass(pass)) {
            //    continue;
            //}

            // Do regular vanilla checks for visibility
            if (!entity.isInRangeToRender3d(renderX, renderY, renderZ) && (entity.ridingEntity == null || entity.riddenByEntity != null)) {
                continue;
            }

            // Check if any corners of the bounding box are in a visible subchunk
            if (!SodiumWorldRenderer.instance().isEntityVisible(entity)) {
                continue;
            }

            boolean isSleeping = renderView instanceof EntityLivingBase && ((EntityLivingBase) renderView).isPlayerSleeping();

            if (!(entity != renderView || this.mc.gameSettings.thirdPersonView != 0 || isSleeping)) {
                continue;
            }

            BlockPos entityBlockPos = new BlockPos((int) entity.posX, (int) entity.posY, (int) entity.posZ);

            if (entity.posY < 0.0D || entity.posY >= 256.0D || this.theWorld.isBlockLoaded(entityBlockPos))
            {
                ++this.countEntitiesRendered;
                this.renderManager.renderEntityStatic(entity, partialTicks, false);
            }
        }

        this.renderer.renderBlockEntities(this.damagedBlocks, partialTicks);

        this.mc.entityRenderer.disableLightmap();
        this.mc.mcProfiler.endSection();
    }

    /**
     * @reason Redirect to our renderer
     * @author Lunasa
     */
    @Overwrite
    public String getDebugInfoRenders() {
        return this.renderer.getChunksDebugString();
    }
}

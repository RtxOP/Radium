package net.caffeinemc.mods.sodium.client.render.chunk.data;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.VisibilityEncoding;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.client.renderer.chunk.ChunkOcclusionData;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.lang.reflect.Array;

/**
 * The render data for a chunk render container containing all the information about which meshes are attached, the
 * block entities contained by it, and any data used for occlusion testing.
 */
public class BuiltSectionInfo {
    public static final BuiltSectionInfo EMPTY = createEmptyData();

    public final int flags;
    public final long[] visibilityData;

    @Nullable public final TileEntity[] globalBlockEntities;
    @Nullable public final TileEntity[] culledBlockEntities;
    @Nullable public final TextureAtlasSprite[] animatedSprites;

    private BuiltSectionInfo(@NotNull Collection<TerrainRenderPass> blockRenderPasses,
                             @NotNull Collection<TileEntity> globalBlockEntities,
                             @NotNull Collection<TileEntity> culledBlockEntities,
                             @NotNull Collection<TextureAtlasSprite> animatedSprites,
                             @NotNull ChunkOcclusionData[] occlusionData) {
        this.globalBlockEntities = toArray(globalBlockEntities, TileEntity.class);
        this.culledBlockEntities = toArray(culledBlockEntities, TileEntity.class);
        this.animatedSprites = toArray(animatedSprites, TextureAtlasSprite.class);

        int flags = 0;

        if (!blockRenderPasses.isEmpty()) {
            flags |= RenderSectionFlags.MASK_HAS_BLOCK_GEOMETRY;
        }

        if (!culledBlockEntities.isEmpty()) {
            flags |= RenderSectionFlags.MASK_HAS_BLOCK_ENTITIES;
        }

        if (!animatedSprites.isEmpty()) {
            flags |= RenderSectionFlags.MASK_HAS_ANIMATED_SPRITES;
        }

        this.flags = flags;

        this.visibilityData = new long[occlusionData.length];
        for (int i = 0; i < occlusionData.length; i++) {
            this.visibilityData[i] = VisibilityEncoding.encode(occlusionData[i]);
        }
    }

    public static class Builder {
        private final List<TerrainRenderPass> blockRenderPasses = new ArrayList<>();
        private final List<TileEntity> globalBlockEntities = new ArrayList<>();
        private final List<TileEntity> culledBlockEntities = new ArrayList<>();
        private final Set<TextureAtlasSprite> animatedSprites = new ObjectOpenHashSet<>();

        private ChunkOcclusionData[] occlusionData;

        public void addRenderPass(TerrainRenderPass pass) {
            this.blockRenderPasses.add(pass);
        }

        public void setOcclusionData(ChunkOcclusionData[] data) {
            this.occlusionData = data;
        }

        /**
         * Adds a sprite to this data container for tracking. If the sprite is tickable, it will be ticked every frame
         * before rendering as necessary.
         * @param sprite The sprite
         */
        public void addTextureAtlasSprite(TextureAtlasSprite sprite) {
            if (sprite.hasAnimationMetadata()) {
                this.animatedSprites.add(sprite);
            }
        }

        /**
         * Adds a block entity to the data container.
         * @param entity The block entity itself
         * @param cull True if the block entity can be culled to this chunk render's volume, otherwise false
         */
        public void addTileEntity(TileEntity entity, boolean cull) {
            (cull ? this.culledBlockEntities : this.globalBlockEntities).add(entity);
        }

        public BuiltSectionInfo build() {
            return new BuiltSectionInfo(this.blockRenderPasses, this.globalBlockEntities, this.culledBlockEntities, this.animatedSprites, this.occlusionData);
        }
    }

    private static BuiltSectionInfo createEmptyData() {
        ChunkOcclusionData occlusionData = new ChunkOcclusionData();
        occlusionData.addOpenEdgeFaces(EnumSet.allOf(EnumFacing.class));

        BuiltSectionInfo.Builder meshInfo = new BuiltSectionInfo.Builder();
        meshInfo.setOcclusionData(new ChunkOcclusionData[] { occlusionData });

        return meshInfo.build();
    }

    private static <T> T[] toArray(Collection<T> collection, Class<T> componentType) {
        if (collection.isEmpty()) {
            return null;
        }

        return collection.toArray((T[]) Array.newInstance(componentType, collection.size()));
    }
}
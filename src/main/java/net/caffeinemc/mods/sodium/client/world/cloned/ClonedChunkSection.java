package net.caffeinemc.mods.sodium.client.world.cloned;

import dev.vexor.radium.compat.mojang.minecraft.ChunkNibbleArrayExt;
import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;
import gg.sona.radium.mixin.sodium.core.access.AChunk;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClonedChunkSection {
    private static final NibbleArray DEFAULT_SKY_LIGHT_ARRAY = new ChunkNibbleArrayExt(15);
    private static final NibbleArray DEFAULT_BLOCK_LIGHT_ARRAY = new NibbleArray();
    private static final IBlockState EMPTY_BLOCK_STATE = Blocks.air.getDefaultState();

    private final SectionPos pos;
    private final @Nullable Int2ReferenceMap<TileEntity> blockEntityMap;
    private final @Nullable NibbleArray[] lightDataArrays;
    private final @Nullable IBlockState[] blockData;
    private final @Nullable BiomeGenBase[] biomeData;

    private long lastUsedTimestamp = Long.MAX_VALUE;

    public ClonedChunkSection(World level, Chunk chunk, @Nullable ExtendedBlockStorage section, SectionPos pos) {
        this.pos = pos;

        BiomeGenBase[] biomeData = null;
        IBlockState[] blockData = null;
        Int2ReferenceMap<TileEntity> blockEntityMap = null;

        if (section != null) {
            if (!section.isEmpty()) {
                char[] blockStates = section.getData();
                if (blockStates != null) {
                    blockData = new IBlockState[blockStates.length];

                    for (int i = 0; i < blockStates.length; i++) {
                    // MCP 1.8.9 divergence: Block.getStateById(int) decodes BLOCK_ID<<12|META, but
                    // ExtendedBlockStorage.getData() stores FLAT state-ids from the Block.BLOCK_STATE_IDS
                    // registry (same as the reference's Block.BLOCK_STATES.fromId). Use the registry lookup.
                    IBlockState state = (IBlockState) Block.BLOCK_STATE_IDS.getByValue(blockStates[i] & 0xFFFF);
                        blockData[i] = state == null ? EMPTY_BLOCK_STATE : state;
                    }
                }

                blockEntityMap = copyBlockEntities(chunk, pos);
            }

            biomeData = convertBiomeArray(chunk.getBiomeArray());
        }

        this.biomeData = biomeData;
        this.blockData = blockData;
        this.blockEntityMap = blockEntityMap;
        this.lightDataArrays = copyLightData(level, section);
    }

    private static BiomeGenBase[] convertBiomeArray(byte[] biomeIds) {
        BiomeGenBase[] biomes = new BiomeGenBase[biomeIds.length];
        for (int i = 0; i < biomeIds.length; i++) {
            // Convert the byte to an unsigned int and fetch the corresponding Biome
            biomes[i] = BiomeGenBase.getBiome(biomeIds[i] & 0xFF);
            if (biomes[i] == null) {
                biomes[i] = BiomeGenBase.plains; // Default to Plains if the biome is not found
            }
        }
        return biomes;
    }

    @NotNull
    private static NibbleArray[] copyLightData(World level, ExtendedBlockStorage section) {
        NibbleArray[] arrays = new NibbleArray[2];

        arrays[EnumSkyBlock.BLOCK.ordinal()] = copyLightArray(section, EnumSkyBlock.BLOCK);

        // Dimensions without sky-light should not have a default-initialized array
        if (!level.provider.getHasNoSky()) {
            arrays[EnumSkyBlock.SKY.ordinal()] = copyLightArray(section, EnumSkyBlock.SKY);
        }

        return arrays;
    }

    /**
     * Copies the light data array for the given light type for this chunk, or returns a default-initialized value if
     * the light array is not loaded.
     */
    @NotNull
    private static NibbleArray copyLightArray(ExtendedBlockStorage section, EnumSkyBlock type) {
        if (section != null) {
            if (type == EnumSkyBlock.SKY) {
                return section.getSkylightArray();
            }
            return section.getBlocklightArray();
        }

        if (type == EnumSkyBlock.SKY) {
            return DEFAULT_SKY_LIGHT_ARRAY;
        }
        return DEFAULT_BLOCK_LIGHT_ARRAY;
    }

    private static @NotNull Int2ReferenceMap<TileEntity> copyBlockEntities(Chunk chunk, SectionPos pos) {
        Int2ReferenceOpenHashMap<TileEntity> blockEntities = new Int2ReferenceOpenHashMap<>();

        for (int y = pos.minBlockY(); y <= pos.maxBlockY(); y++) {
            for (int z = pos.minBlockZ(); z <= pos.maxBlockZ(); z++) {
                for (int x = pos.minBlockX(); x <= pos.maxBlockX(); x++) {
                    Block block = ((AChunk) chunk).invokeGetBlock(x & 15, y, z & 15);
                    if (!block.hasTileEntity()) {
                        continue;
                    }

                    TileEntity blockEntity = chunk.getTileEntity(new BlockPos(x, y, z), Chunk.EnumCreateEntityType.IMMEDIATE);
                    if (blockEntity != null) {
                        blockEntities.put(LevelSlice.getLocalBlockIndex(x & 15, y & 15, z & 15), blockEntity);
                    }
                }
            }
        }

        return blockEntities;
    }

    public SectionPos getPosition() {
        return this.pos;
    }

    public @Nullable IBlockState[] getBlockData() {
        return this.blockData;
    }

    public @Nullable BiomeGenBase[] getBiomeData() {
        return this.biomeData;
    }

    public @Nullable Int2ReferenceMap<TileEntity> getBlockEntityMap() {
        return this.blockEntityMap;
    }

    public @Nullable NibbleArray getLightArray(EnumSkyBlock type) {
        return this.lightDataArrays[type.ordinal()];
    }

    public long getLastUsedTimestamp() {
        return this.lastUsedTimestamp;
    }

    public void setLastUsedTimestamp(long timestamp) {
        this.lastUsedTimestamp = timestamp;
    }
}

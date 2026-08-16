package net.caffeinemc.mods.sodium.client.world;

import dev.vexor.radium.compat.mojang.math.Mth;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.world.biome.BiomeColorSource;
import net.caffeinemc.mods.sodium.client.world.biome.LevelColorCache;
import net.caffeinemc.mods.sodium.client.world.biome.LevelBiomeSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import dev.vexor.radium.compat.mojang.minecraft.math.BlockBox;
import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * <p>Takes a slice of level state (block states, biome and light data arrays) and copies the data for use in off-thread
 * operations. This allows chunk build tasks to see a consistent snapshot of chunk data at the exact moment the task was
 * created.</p>
 *
 * <p>World slices are not safe to use from multiple threads at once, but the data they contain is safe from modification
 * by the main client thread.</p>
 *
 * <p>Object pooling should be used to avoid huge allocations as this class contains many large arrays.</p>
 */
public final class LevelSlice implements IBlockAccess {
    private static final EnumSkyBlock[] LIGHT_TYPES = EnumSkyBlock.values();

    // The number of blocks in a section.
    private static final int SECTION_BLOCK_COUNT = 16 * 16 * 16;

    // The radius of blocks around the origin chunk that should be copied.
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;

    // The radius of chunks around the origin chunk that should be copied.
    private static final int NEIGHBOR_CHUNK_RADIUS = Mth.roundToward(NEIGHBOR_BLOCK_RADIUS, 16) >> 4;

    // The number of sections on each axis of this slice.
    private static final int SECTION_ARRAY_LENGTH = 1 + (NEIGHBOR_CHUNK_RADIUS * 2);

    // The size of the (Local Section -> Resource) arrays.
    private static final int SECTION_ARRAY_SIZE = SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH;

    // The number of bits needed for each local X/Y/Z coordinate.
    private static final int LOCAL_XYZ_BITS = 4;

    // The default block state used for out-of-bounds access
    private static final IBlockState EMPTY_BLOCK_STATE = Blocks.air.getDefaultState();

    // The level this slice has copied data from
    private final WorldClient level;

    // The accessor used for fetching biome data from the slice
    private final LevelBiomeSlice biomeSlice;

    // The biome blend cache
    private final LevelColorCache biomeColors;

    // (Local Section -> Block States) table.
    private final IBlockState[][] blockArrays;

    // (Local Section -> Light Arrays) table.
    private final @Nullable NibbleArray[][] lightArrays;

    // (Local Section -> Block Entity) table.
    private final @Nullable Int2ReferenceMap<TileEntity>[] blockEntityArrays;

    // The starting point from which this slice captures blocks
    private int originBlockX, originBlockY, originBlockZ;

    // The volume that this WorldSlice contains
    private BlockBox volume;

    private final int[] defaultLightValues;

    public static ChunkRenderContext prepare(World level, SectionPos pos, ClonedChunkSectionCache cache) {
        // MCP 1.8.9: getChunkFromChunkCoords auto-generates; only proceed if the chunk is loaded.
        if (!level.getChunkProvider().chunkExists(pos.getX(), pos.getZ())) {
            return null;
        }

        Chunk chunk = level.getChunkFromChunkCoords(pos.getX(), pos.getZ());
        ExtendedBlockStorage section = chunk.getBlockStorageArray()[pos.getY()];

        // If the chunk section is absent or empty, simply terminate now. There will never be anything in this chunk
        // section to render, so we need to signal that a chunk render task shouldn't be created. This saves a considerable
        // amount of time in queueing instant build tasks and greatly accelerates how quickly the level can be loaded.
        if (section == null || section.isEmpty()) {
            return null;
        }

        BlockBox box = new BlockBox(pos.minBlockX() - NEIGHBOR_BLOCK_RADIUS,
                pos.minBlockY() - NEIGHBOR_BLOCK_RADIUS,
                pos.minBlockZ() - NEIGHBOR_BLOCK_RADIUS,
                pos.maxBlockX() + NEIGHBOR_BLOCK_RADIUS,
                pos.maxBlockY() + NEIGHBOR_BLOCK_RADIUS,
                pos.maxBlockZ() + NEIGHBOR_BLOCK_RADIUS);

        // The min/max bounds of the chunks copied by this slice
        final int minChunkX = pos.getX() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkY = pos.getY() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkZ = pos.getZ() - NEIGHBOR_CHUNK_RADIUS;

        final int maxChunkX = pos.getX() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkY = pos.getY() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkZ = pos.getZ() + NEIGHBOR_CHUNK_RADIUS;

        ClonedChunkSection[] sections = new ClonedChunkSection[SECTION_ARRAY_SIZE];

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    sections[getLocalSectionIndex(chunkX - minChunkX, chunkY - minChunkY, chunkZ - minChunkZ)] =
                            cache.acquire(chunkX, chunkY, chunkZ);
                }
            }
        }

        return new ChunkRenderContext(pos, sections, box);
    }

    @SuppressWarnings("unchecked")
    public LevelSlice(WorldClient level) {
        this.level = level;

        defaultLightValues = new int[LIGHT_TYPES.length];
        defaultLightValues[EnumSkyBlock.SKY.ordinal()] = level.provider.getHasNoSky() ? 0 : 15;
        defaultLightValues[EnumSkyBlock.BLOCK.ordinal()] = 0;

        this.blockArrays = new IBlockState[SECTION_ARRAY_SIZE][SECTION_BLOCK_COUNT];
        this.lightArrays = new NibbleArray[SECTION_ARRAY_SIZE][LIGHT_TYPES.length];

        this.blockEntityArrays = new Int2ReferenceMap[SECTION_ARRAY_SIZE];

        int biomeBlendRadius = SodiumClientMod.options().quality.biomeBlendRadius;

        this.biomeSlice = new LevelBiomeSlice();
        this.biomeColors = new LevelColorCache(this.biomeSlice, biomeBlendRadius);

        for (IBlockState[] blockArray : this.blockArrays) {
            Arrays.fill(blockArray, EMPTY_BLOCK_STATE);
        }
    }

    public void copyData(ChunkRenderContext context) {
        this.originBlockX = SectionPos.sectionToBlockCoord(context.origin().getX() - NEIGHBOR_CHUNK_RADIUS);
        this.originBlockY = SectionPos.sectionToBlockCoord(context.origin().getY() - NEIGHBOR_CHUNK_RADIUS);
        this.originBlockZ = SectionPos.sectionToBlockCoord(context.origin().getZ() - NEIGHBOR_CHUNK_RADIUS);

        this.volume = context.volume();

        for (int x = 0; x < SECTION_ARRAY_LENGTH; x++) {
            for (int y = 0; y < SECTION_ARRAY_LENGTH; y++) {
                for (int z = 0; z < SECTION_ARRAY_LENGTH; z++) {
                    this.copySectionData(context, getLocalSectionIndex(x, y, z));
                }
            }
        }

        this.biomeSlice.update(this.level, context);
        this.biomeColors.update(context);
    }

    private void copySectionData(ChunkRenderContext context, int sectionIndex) {
        ClonedChunkSection section = context.sections()[sectionIndex];

        Objects.requireNonNull(section, "Chunk section must be non-null");

        this.unpackBlockData(this.blockArrays[sectionIndex], context, section);

        this.lightArrays[sectionIndex][EnumSkyBlock.BLOCK.ordinal()] = section.getLightArray(EnumSkyBlock.BLOCK);
        this.lightArrays[sectionIndex][EnumSkyBlock.SKY.ordinal()] = section.getLightArray(EnumSkyBlock.SKY);

        this.blockEntityArrays[sectionIndex] = section.getBlockEntityMap();
    }

    private void unpackBlockData(IBlockState[] blockArray, ChunkRenderContext context, ClonedChunkSection section) {
        IBlockState[] container = section.getBlockData();
        if (container == null) {
            Arrays.fill(blockArray, EMPTY_BLOCK_STATE);
            return;
        }

        SectionPos sectionPos = section.getPosition();
        if (sectionPos.equals(context.origin())) {
            System.arraycopy(container, 0, blockArray, 0, container.length);
        } else {
            BlockBox bounds = context.volume();

            int minBlockX = Math.max(bounds.minX, sectionPos.minBlockX());
            int maxBlockX = Math.min(bounds.maxX, sectionPos.maxBlockX());

            int minBlockY = Math.max(bounds.minY, sectionPos.minBlockY());
            int maxBlockY = Math.min(bounds.maxY, sectionPos.maxBlockY());

            int minBlockZ = Math.max(bounds.minZ, sectionPos.minBlockZ());
            int maxBlockZ = Math.min(bounds.maxZ, sectionPos.maxBlockZ());

            for (int x = minBlockX; x <= maxBlockX; x++) {
                for (int y = minBlockY; y <= maxBlockY; y++) {
                    for (int z = minBlockZ; z <= maxBlockZ; z++) {
                        int index = getLocalBlockIndex(x & 15, y & 15, z & 15);
                        blockArray[index] = container[index];
                    }
                }
            }
        }
    }


    public void reset() {
        // erase any pointers to resources we no longer need
        // no point in cleaning the pre-allocated arrays (such as block state storage) since we hold the
        // only reference.
        for (int sectionIndex = 0; sectionIndex < SECTION_ARRAY_LENGTH; sectionIndex++) {
            Arrays.fill(this.lightArrays[sectionIndex], null);

            this.blockEntityArrays[sectionIndex] = null;
        }
    }

    public WorldClient getLevel() {
        return this.level;
    }

    public @NotNull IBlockState getBlockState(BlockPos pos) {
        if (!this.volume.contains(pos)) {
            return EMPTY_BLOCK_STATE;
        }

        int relBlockX = pos.getX() - this.originBlockX;
        int relBlockY = pos.getY() - this.originBlockY;
        int relBlockZ = pos.getZ() - this.originBlockZ;

        return this.blockArrays[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)]
                [getLocalBlockIndex(relBlockX & 15, relBlockY & 15, relBlockZ & 15)];
    }

    public boolean isAir(BlockPos pos) {
        return getBlockState(pos).getBlock() == Blocks.air;
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return this.isAir(pos);
    }

    public BiomeGenBase getBiome(BlockPos pos) {
        return this.biomeSlice.getBiome(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isEmpty() {
        return false;
    }

    public int getStrongRedstonePower(BlockPos pos, EnumFacing direction) {
        IBlockState blockState = this.getBlockState(pos);
        return blockState.getBlock().getStrongPower(this, pos, blockState, direction);
    }

    public WorldType getGeneratorType() {
        return this.level.getWorldType();
    }

    public int getLight(EnumSkyBlock type, BlockPos pos) {
        if (!this.volume.contains(pos)) {
            return 0;
        }

        int relX = pos.getX() - originBlockX;
        int relY = pos.getY() - originBlockY;
        int relZ = pos.getZ() - originBlockZ;

        IBlockState state = getBlockStateRelative(relX, relY, relZ);

        if (!state.getBlock().getUseNeighborBrightness()) {
            return getLightFor(type, relX, relY, relZ);
        } else {
            int west = getLightFor(type, relX - 1, relY, relZ);
            int east = getLightFor(type, relX + 1, relY, relZ);
            int up = getLightFor(type, relX, relY + 1, relZ);
            int down = getLightFor(type, relX, relY - 1, relZ);
            int north = getLightFor(type, relX, relY, relZ + 1);
            int south = getLightFor(type, relX, relY, relZ - 1);

            if (east > west) {
                west = east;
            }

            if (up > west) {
                west = up;
            }

            if (down > west) {
                west = down;
            }

            if (north > west) {
                west = north;
            }

            if (south > west) {
                west = south;
            }

            return west;
        }
    }

    private int getLightFor(EnumSkyBlock type, int relX, int relY, int relZ) {
        int sectionIdx = getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4);

        NibbleArray lightArray = lightArrays[sectionIdx][type.ordinal()];
        if (lightArray == null) {
            // If the array is null, it means the dimension for the current world does not support that light type
            return defaultLightValues[type.ordinal()];
        }

        return lightArray.get(relX & 15, relY & 15, relZ & 15);
    }


    public IBlockState getBlockStateRelative(int x, int y, int z) {
        // NOTE: Not bounds checked. We assume ChunkRenderRebuildTask is the only function using this
        int sectionIdx = getLocalSectionIndex(x >> 4, y >> 4, z >> 4);
        int blockIdx = getLocalBlockIndex(x & 15, y & 15, z & 15);

        return blockArrays[sectionIdx][blockIdx];
    }

    public int getLight(BlockPos pos, int ambientDarkness) {
        if (!this.volume.contains(pos)) {
            return 0;
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (y < 0 || y >= 256 || x < -30_000_000 || z < -30_000_000 || x >= 30_000_000 || z >= 30_000_000) {
            return (defaultLightValues[0] << 20) | (ambientDarkness << 4);
        }

        int skyBrightness = getLight(EnumSkyBlock.SKY, pos);
        int blockBrightness = getLight(EnumSkyBlock.BLOCK, pos);

        if (blockBrightness < ambientDarkness) {
            blockBrightness = ambientDarkness;
        }

        return skyBrightness << 20 | blockBrightness << 4;
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return this.getBlockEntity(pos);
    }

    public TileEntity getBlockEntity(BlockPos pos) {
        if (!this.volume.contains(pos)) {
            return null;
        }

        int relBlockX = pos.getX() - this.originBlockX;
        int relBlockY = pos.getY() - this.originBlockY;
        int relBlockZ = pos.getZ() - this.originBlockZ;

        Int2ReferenceMap<TileEntity> blockEntities = this.blockEntityArrays[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)];

        if (blockEntities == null) {
            return null;
        }

        return blockEntities.get(getLocalBlockIndex(relBlockX & 15, relBlockY & 15, relBlockZ & 15));
    }

    public static int getLocalBlockIndex(int blockX, int blockY, int blockZ) {
        return (blockY << LOCAL_XYZ_BITS << LOCAL_XYZ_BITS) | (blockZ << LOCAL_XYZ_BITS) | blockX;
    }

    public static int getLocalSectionIndex(int sectionX, int sectionY, int sectionZ) {
        return (sectionY * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH) + (sectionZ * SECTION_ARRAY_LENGTH) + sectionX;
    }

    public float getBrightness(EnumFacing direction, boolean shaded) {
        // todo(investigate): the directions differ from vanilla
        if (direction == EnumFacing.DOWN) {
            return .5f;
        }
        if (direction == EnumFacing.UP) {
            return 1f;
        }
        if (direction == EnumFacing.NORTH || direction == EnumFacing.SOUTH) {
            return .8f;
        }
        return .6f;
    }


    @Override
    public int getCombinedLight(BlockPos pos, int combinedLight) {
        return this.getLight(pos, combinedLight);
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        IBlockState blockState = this.getBlockState(pos);
        return blockState.getBlock().getStrongPower(this, pos, blockState, direction);
    }

    @Override
    public net.minecraft.world.WorldType getWorldType() {
        return this.level.getWorldType();
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        return this.getBlockState(pos).getBlock().isSideSolid(this, pos, side);
    }

    @Override
    public net.minecraft.world.biome.BiomeGenBase getBiomeGenForCoords(BlockPos pos) {
        return this.biomeSlice.getBiome(pos.getX(), pos.getY(), pos.getZ());
    }

    public int getColor(BiomeColorSource source, int blockX, int blockY, int blockZ) {
        return this.biomeColors.getColor(source.getProvider(), blockX, blockY, blockZ);
    }
}

package dev.vexor.radium.compat.mojang.minecraft.math;

import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3i;
import net.minecraft.world.ChunkCoordIntPair;

/**
 * MCP 1.8.9 port of Radium's Ornithe bridge: {@link MathHelper#floor_double} replaces
 * {@code MathHelper.floor(double)} and ChunkCoordIntPair fields are {@code chunkXPos}/{@code chunkZPos}.
 */
public class SectionPos extends Vec3i {
    public static final int SECTION_BITS = 4;
    public static final int SECTION_MAX_INDEX = 15;
    private static final long PACKED_X_MASK = 0x3FFFFFL;
    private static final long PACKED_Y_MASK = 0xFFFFFL;
    private static final long PACKED_Z_MASK = 0x3FFFFFL;
    private static final int Y_OFFSET = 0;
    private static final int Z_OFFSET = 20;
    private static final int X_OFFSET = 42;

    SectionPos(int x, int y, int z) {
        super(x, y, z);
    }

    public static SectionPos of(int x, int y, int z) {
        return new SectionPos(x, y, z);
    }

    public static SectionPos of(BlockPos pos) {
        int x = blockToSectionCoord(pos.getX());
        int y = blockToSectionCoord(pos.getY());
        int z = blockToSectionCoord(pos.getZ());
        return new SectionPos(x, y, z);
    }

    public static SectionPos of(ChunkCoordIntPair pos, int y) {
        return new SectionPos(blockToSectionCoord(pos.chunkXPos), y, blockToSectionCoord(pos.chunkZPos));
    }

    public static SectionPos of(long packed) {
        return new SectionPos(x(packed), y(packed), z(packed));
    }

    public static int posToSectionCoord(double d) {
        return blockToSectionCoord(MathHelper.floor_double(d));
    }

    public static int blockToSectionCoord(int n) {
        return n >> SECTION_BITS;
    }

    public static int sectionToBlockCoord(int n) {
        return n << SECTION_BITS;
    }

    public static int sectionToBlockCoord(int n, int n2) {
        return sectionToBlockCoord(n) + n2;
    }

    public static int x(long packed) {
        return (int) ((packed >> X_OFFSET) & PACKED_X_MASK);
    }

    public static int y(long packed) {
        return (int) (packed & PACKED_Y_MASK);
    }

    public static int z(long packed) {
        return (int) ((packed >> Z_OFFSET) & PACKED_Z_MASK);
    }

    public int x() {
        return getX();
    }

    public int y() {
        return getY();
    }

    public int z() {
        return getZ();
    }

    public int minBlockX() {
        return sectionToBlockCoord(x());
    }

    public int minBlockY() {
        return sectionToBlockCoord(y());
    }

    public int minBlockZ() {
        return sectionToBlockCoord(z());
    }

    public int maxBlockX() {
        return sectionToBlockCoord(x(), SECTION_MAX_INDEX);
    }

    public int maxBlockY() {
        return sectionToBlockCoord(y(), SECTION_MAX_INDEX);
    }

    public int maxBlockZ() {
        return sectionToBlockCoord(z(), SECTION_MAX_INDEX);
    }

    public BlockPos origin() {
        int x = sectionToBlockCoord(x());
        int y = sectionToBlockCoord(y());
        int z = sectionToBlockCoord(z());
        return new BlockPos(x, y, z);
    }

    public ChunkCoordIntPair chunk() {
        return new ChunkCoordIntPair(x(), z());
    }

    public static long asLong(int x, int y, int z) {
        long packed = 0L;
        packed |= ((long) x & PACKED_X_MASK) << X_OFFSET;
        packed |= ((long) y & PACKED_Y_MASK) << Y_OFFSET;
        packed |= ((long) z & PACKED_Z_MASK) << Z_OFFSET;
        return packed;
    }

    public long asLong() {
        return asLong(x(), y(), z());
    }
}

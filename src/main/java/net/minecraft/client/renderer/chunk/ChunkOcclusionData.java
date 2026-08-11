package net.minecraft.client.renderer.chunk;

import net.minecraft.util.EnumFacing;

/**
 * SodiumForge compat: MCP 1.8.9 has no ChunkOcclusionData (vanilla 1.8.9 only has
 * Voronoi VisGraph at this package). Radium's tree-based culling needs the modern
 * Sodium contract ({@code fill}/{@code setVisibleThrough}/{@code isVisibleThrough})
 * backed by the face-pair bit encoding used by
 * {@code net.caffeinemc.mods.sodium.client.render.chunk.occlusion.VisibilityEncoding}
 * (bit = from*8+to, from/to = GraphDirection ordinal == EnumFacing ordinal).
 * With 6 directions this needs only 48 of the 64 mask bits.
 */
public class ChunkOcclusionData {
    private long mask;

    public static int bit(EnumFacing from, EnumFacing to) {
        return from.getIndex() * 8 + to.getIndex();
    }

    public void setVisibleThrough(EnumFacing from, EnumFacing to, boolean visible) {
        long bit = 1L << bit(from, to);

        if (visible) {
            this.mask |= bit;
        } else {
            this.mask &= ~bit;
        }
    }

    public boolean isVisibleThrough(EnumFacing from, EnumFacing to) {
        return (this.mask & (1L << bit(from, to))) != 0;
    }

    public void fill(boolean visible) {
        this.mask = visible ? -1L : 0L;
    }

    /**
     * Marks the given set of faces as open edges of the chunk volume: light and
     * visibility pass through them in both directions. (Sodium contract used by
     * BuiltSectionInfo for EMPTY sections so they never occlude neighbors.)
     */
    public void addOpenEdgeFaces(java.util.Set<EnumFacing> openFaces) {
        for (EnumFacing open : openFaces) {
            for (int g = 0; g < 6; g++) {
                EnumFacing other = EnumFacing.getFront(g);
                this.mask |= (1L << bit(open, other)) | (1L << bit(other, open));
            }
        }
    }

    public long getVisibilityMask() {
        return this.mask;
    }
}

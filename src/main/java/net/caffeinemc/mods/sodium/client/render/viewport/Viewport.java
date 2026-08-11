package net.caffeinemc.mods.sodium.client.render.viewport;

import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public final class Viewport {
    // The bounding box of a chunk section must be large enough to contain all possible geometry within it. Block models
    // can extend outside a block volume by +/- 1.0 blocks on all axis. Additionally, we make use of a small epsilon
    // to deal with floating point imprecision during a frustum check (see GH#2132).
    public static final float CHUNK_SECTION_RADIUS = 8.0f /* chunk bounds */;
    public static final float CHUNK_SECTION_MARGIN = 1.0f /* maximum model extent */ + 0.125f /* epsilon */;
    public static final float CHUNK_SECTION_NEARBY_MARGIN = 2.0f /* larger model extent */ + 0.125f /* epsilon */;
    public static final float CHUNK_SECTION_PADDED_RADIUS = CHUNK_SECTION_RADIUS + CHUNK_SECTION_MARGIN;
    private static final float LOOSER_MARGIN_EXTRA = CHUNK_SECTION_NEARBY_MARGIN - CHUNK_SECTION_MARGIN;

    private final Frustum frustum;
    private final CameraTransform transform;

    private final SectionPos sectionCoords;
    private final BlockPos blockCoords;

    public Viewport(Frustum frustum, Vec3 position) {
        this.frustum = frustum;
        this.transform = new CameraTransform(position.xCoord, position.yCoord, position.zCoord);

        this.sectionCoords = SectionPos.of(
                SectionPos.posToSectionCoord(position.xCoord),
                SectionPos.posToSectionCoord(position.yCoord),
                SectionPos.posToSectionCoord(position.zCoord)
        );

        this.blockCoords = new BlockPos(
                MathHelper.floor_double(position.xCoord),
                MathHelper.floor_double(position.yCoord),
                MathHelper.floor_double(position.zCoord)
        );
    }

    public boolean isBoxVisible(int intOriginX, int intOriginY, int intOriginZ) {
        float floatOriginX = (intOriginX - this.transform.intX) - this.transform.fracX;
        float floatOriginY = (intOriginY - this.transform.intY) - this.transform.fracY;
        float floatOriginZ = (intOriginZ - this.transform.intZ) - this.transform.fracZ;

        return this.frustum.testSection(floatOriginX, floatOriginY, floatOriginZ);
    }

    public boolean isBoxVisibleLooser(int intOriginX, int intOriginY, int intOriginZ) {
        float floatOriginX = (intOriginX - this.transform.intX) - this.transform.fracX;
        float floatOriginY = (intOriginY - this.transform.intY) - this.transform.fracY;
        float floatOriginZ = (intOriginZ - this.transform.intZ) - this.transform.fracZ;

        return this.frustum.testSectionExpanded(floatOriginX, floatOriginY, floatOriginZ, LOOSER_MARGIN_EXTRA);
    }

    public boolean isBoxVisibleDirect(float floatOriginX, float floatOriginY, float floatOriginZ, float floatSize) {
        return this.frustum.testAab(
                floatOriginX - floatSize,
                floatOriginY - floatSize,
                floatOriginZ - floatSize,

                floatOriginX + floatSize,
                floatOriginY + floatSize,
                floatOriginZ + floatSize
        );
    }

    public int getBoxIntersectionDirect(float floatOriginX, float floatOriginY, float floatOriginZ, float floatSize) {
        return this.frustum.intersectAab(
                floatOriginX - floatSize,
                floatOriginY - floatSize,
                floatOriginZ - floatSize,

                floatOriginX + floatSize,
                floatOriginY + floatSize,
                floatOriginZ + floatSize
        );
    }

    public CameraTransform getTransform() {
        return this.transform;
    }

    public SectionPos getChunkCoord() {
        return this.sectionCoords;
    }

    public BlockPos getBlockCoord() {
        return this.blockCoords;
    }
}

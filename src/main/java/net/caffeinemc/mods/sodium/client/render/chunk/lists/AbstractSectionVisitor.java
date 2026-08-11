package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;
import net.minecraft.util.BlockPos;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.util.MathHelper;

public abstract class AbstractSectionVisitor implements OcclusionCuller.GraphOcclusionVisitor {
    protected final boolean isFrustumTested;
    protected final int baseOffsetX, baseOffsetY, baseOffsetZ;

    protected final int cameraX, cameraY, cameraZ;

    public AbstractSectionVisitor(Viewport viewport, float buildDistance, boolean frustumTested) {
        this.isFrustumTested = frustumTested;
        int offsetDistance = MathHelper.ceiling_float_int(buildDistance / 16.0f) + 1;

        // the offset applied to section coordinates to encode their position in the octree
        SectionPos sectionPos = viewport.getChunkCoord();
        int cameraSectionX = sectionPos.getX();
        int cameraSectionY = sectionPos.getY();
        int cameraSectionZ = sectionPos.getZ();
        this.baseOffsetX = cameraSectionX - offsetDistance;
        this.baseOffsetY = cameraSectionY - offsetDistance;
        this.baseOffsetZ = cameraSectionZ - offsetDistance;

        if (frustumTested) {
            BlockPos blockPos = viewport.getBlockCoord();
            this.cameraX = blockPos.getX();
            this.cameraY = blockPos.getY();
            this.cameraZ = blockPos.getZ();
        } else {
            this.cameraX = (cameraSectionX << 4);
            this.cameraY = (cameraSectionY << 4);
            this.cameraZ = (cameraSectionZ << 4);
        }
    }
}
package dev.vexor.radium.compat.mojang.minecraft.math;

import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3i;

/**
 * MCP 1.8.9 stand-in for Yarn's {@code net.minecraft.util.math.BlockBox} (used by
 * Radium's world-slice volume). 1.8.9 has no AABB-free int box type; this one
 * mirrors the Yarn API (public min/max fields + contains(Vec3i)).
 */
public class BlockBox {
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    public BlockBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public boolean contains(Vec3i pos) {
        return pos.getX() >= this.minX && pos.getX() <= this.maxX
                && pos.getY() >= this.minY && pos.getY() <= this.maxY
                && pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
    }

    public boolean contains(BlockPos pos) {
        return this.contains((Vec3i) pos);
    }
}

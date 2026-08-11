package net.caffeinemc.mods.sodium.client.model.quad.properties;

import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.util.EnumFacing;

public class ModelQuadFlags {
    /**
     * Indicates that the quad does not fully cover the given face for the model.
     */
    public static final int IS_PARTIAL = 0b001;

    /**
     * Indicates that the quad is parallel to its light face.
     */
    public static final int IS_PARALLEL = 0b010;

    /**
     * Indicates that the quad is aligned to the block grid.
     * This flag is only set if {@link #IS_PARALLEL} is set.
     */
    public static final int IS_ALIGNED = 0b100;

    /**
     * Number of flags.
     */
    public static final int FLAG_BIT_COUNT = 3;


    /**
     * @return True if the bit-flag of {@link ModelQuadFlags} contains the given flag
     */
    public static boolean contains(int flags, int mask) {
        return (flags & mask) != 0;
    }

    /**
     * Calculates the properties of the given quad. This data is used later by the light pipeline in order to make
     * certain optimizations.
     */
    public static int getQuadFlags(ModelQuadView quad, EnumFacing face) {
        float minX = 32.0F;
        float minY = 32.0F;
        float minZ = 32.0F;

        float maxX = -32.0F;
        float maxY = -32.0F;
        float maxZ = -32.0F;

        for (int i = 0; i < 4; ++i) {
            float x = quad.getX(i);
            float y = quad.getY(i);
            float z = quad.getZ(i);

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        boolean partial;
        EnumFacing.Axis axis = face.getAxis();
        if (axis == EnumFacing.Axis.X) {
            partial = minY >= 0.0001f || minZ >= 0.0001f || maxY <= 0.9999F || maxZ <= 0.9999F;
        } else if (axis == EnumFacing.Axis.Y) {
            partial = minX >= 0.0001f || minZ >= 0.0001f || maxX <= 0.9999F || maxZ <= 0.9999F;
        } else {
            partial = minX >= 0.0001f || minY >= 0.0001f || maxX <= 0.9999F || maxY <= 0.9999F;
        }

        boolean parallel;
        if (axis == EnumFacing.Axis.X) {
            parallel = minX == maxX;
        } else if (axis == EnumFacing.Axis.Y) {
            parallel = minY == maxY;
        } else {
            parallel = minZ == maxZ;
        }

        boolean aligned = parallel;
        if (aligned) {
            if (face == EnumFacing.DOWN) {
                aligned = minY < 0.0001f;
            } else if (face == EnumFacing.UP) {
                aligned = maxY > 0.9999F;
            } else if (face == EnumFacing.NORTH) {
                aligned = minZ < 0.0001f;
            } else if (face == EnumFacing.SOUTH) {
                aligned = maxZ > 0.9999F;
            } else if (face == EnumFacing.WEST) {
                aligned = minX < 0.0001f;
            } else {
                aligned = maxX > 0.9999F;
            }
        }

        int flags = 0;

        if (partial) {
            flags |= IS_PARTIAL;
        }

        if (parallel) {
            flags |= IS_PARALLEL;
        }

        if (aligned) {
            flags |= IS_ALIGNED;
        }

        return flags;
    }
}

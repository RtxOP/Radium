package net.caffeinemc.mods.sodium.client.util;

import net.minecraft.util.EnumFacing;

/**
 * Contains a number of cached arrays to avoid allocations since calling Enum#values() requires the backing array to
 * be cloned every time.
 *
 * <p>Java 8 / MCP adaptation of the reference (Yarn {@code Direction}): Yarn's {@code Direction#values()} has a
 * different order than MCP's, but cached arrays preserve the reference's intent.</p>
 */
public class DirectionUtil {
    public static final EnumFacing[] ALL_DIRECTIONS = EnumFacing.values();

    // Provides the same order as enumerating Direction and checking the axis of each value
    public static final EnumFacing[] HORIZONTAL_DIRECTIONS = new EnumFacing[] { EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST };

    private static final EnumFacing[] OPPOSITE_DIRECTIONS;

    static {
        EnumFacing[] values = ALL_DIRECTIONS;
        OPPOSITE_DIRECTIONS = new EnumFacing[values.length];
        for (int i = 0; i < values.length; i++) {
            OPPOSITE_DIRECTIONS[i] = values[i].getOpposite();
        }
    }

    // Direction#byId is slow in the absence of Lithium
    public static EnumFacing getOpposite(EnumFacing dir) {
        return OPPOSITE_DIRECTIONS[dir.ordinal()];
    }
}

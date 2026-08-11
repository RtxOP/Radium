package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.minecraft.util.EnumFacing;

/**
 * MCP 1.8.9 port: {@code net.minecraft.util.math.Direction} is {@link EnumFacing}.
 */
public class GraphDirection {
    public static final int DOWN    = 0;
    public static final int UP      = 1;
    public static final int NORTH   = 2;
    public static final int SOUTH   = 3;
    public static final int WEST    = 4;
    public static final int EAST    = 5;

    public static final int COUNT   = 6;

    private static final EnumFacing[] ENUMS;
    private static final int[] X;
    private static final int[] Y;
    private static final int[] Z;

    static {
        X = new int[COUNT];
        X[WEST] = -1;
        X[EAST] = 1;

        Y = new int[COUNT];
        Y[DOWN] = -1;
        Y[UP] = 1;

        Z = new int[COUNT];
        Z[NORTH] = -1;
        Z[SOUTH] = 1;

        ENUMS = new EnumFacing[COUNT];
        ENUMS[DOWN] = EnumFacing.DOWN;
        ENUMS[UP] = EnumFacing.UP;
        ENUMS[NORTH] = EnumFacing.NORTH;
        ENUMS[SOUTH] = EnumFacing.SOUTH;
        ENUMS[WEST] = EnumFacing.WEST;
        ENUMS[EAST] = EnumFacing.EAST;
    }

    public static int opposite(int direction) {
        return direction ^ 1;
    }

    public static int x(int direction) {
        return X[direction];
    }

    public static int y(int direction) {
        return Y[direction];
    }

    public static int z(int direction) {
        return Z[direction];
    }

    public static EnumFacing toEnum(int direction) {
        return ENUMS[direction];
    }
}

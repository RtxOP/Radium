package net.caffeinemc.mods.sodium.client.model.quad.properties;

import net.caffeinemc.mods.sodium.client.util.DirectionUtil;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import dev.vexor.radium.compat.mojang.math.Mth;
import net.minecraft.util.EnumFacing;
import org.joml.Math;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;


public enum ModelQuadFacing {
    POS_X,
    POS_Y,
    POS_Z,
    NEG_X,
    NEG_Y,
    NEG_Z,
    UNASSIGNED;

    public static final ModelQuadFacing[] VALUES = ModelQuadFacing.values();

    public static final int COUNT = VALUES.length;
    public static final int DIRECTIONS = VALUES.length - 1;
    public static final int UNASSIGNED_ORDINAL = ModelQuadFacing.UNASSIGNED.ordinal();

    public static final int NONE = 0;
    public static final int ALL = (1 << COUNT) - 1;

    public static final Vector3fc[] ALIGNED_NORMALS = new Vector3fc[] {
            new Vector3f(1, 0, 0),
            new Vector3f(0, 1, 0),
            new Vector3f(0, 0, 1),
            new Vector3f(-1, 0, 0),
            new Vector3f(0, -1, 0),
            new Vector3f(0, 0, -1),
    };

    public static final int[] PACKED_ALIGNED_NORMALS = Arrays.stream(ALIGNED_NORMALS)
            .mapToInt(NormI8::pack)
            .toArray();

    public static final int OPPOSING_X = 1 << ModelQuadFacing.POS_X.ordinal() | 1 << ModelQuadFacing.NEG_X.ordinal();
    public static final int OPPOSING_Y = 1 << ModelQuadFacing.POS_Y.ordinal() | 1 << ModelQuadFacing.NEG_Y.ordinal();
    public static final int OPPOSING_Z = 1 << ModelQuadFacing.POS_Z.ordinal() | 1 << ModelQuadFacing.NEG_Z.ordinal();
    public static final int UNASSIGNED_MASK = 1 << ModelQuadFacing.UNASSIGNED.ordinal();

    public static ModelQuadFacing fromDirection(EnumFacing dir) {
        ModelQuadFacing __swt; switch (dir) {
            case DOWN: __swt = NEG_Y; break;
            case UP: __swt = POS_Y; break;
            case NORTH: __swt = NEG_Z; break;
            case SOUTH: __swt = POS_Z; break;
            case WEST: __swt = NEG_X; break;
            case EAST: __swt = POS_X; break;
            default: __swt = UNASSIGNED; break;
        }
        return __swt;
    }

    public ModelQuadFacing getOpposite() {
        ModelQuadFacing __swt; switch (this) {
            case POS_Y: __swt = NEG_Y; break;
            case NEG_Y: __swt = POS_Y; break;
            case POS_X: __swt = NEG_X; break;
            case NEG_X: __swt = POS_X; break;
            case POS_Z: __swt = NEG_Z; break;
            case NEG_Z: __swt = POS_Z; break;
            default: __swt = UNASSIGNED; break;
        }
        return __swt;
    }

    public int getSign() {
        int __swt; switch (this) {
            case POS_Y:
            case POS_X:
            case POS_Z: __swt = 1; break;
            case NEG_Y:
            case NEG_X:
            case NEG_Z: __swt = -1; break;
            default: __swt = 0; break;
        }
        return __swt;
    }

    public int getAxis() {
        int __swt; switch (this) {
            case POS_X:
            case NEG_X: __swt = 0; break;
            case POS_Y:
            case NEG_Y: __swt = 1; break;
            case POS_Z:
            case NEG_Z: __swt = 2; break;
            default: __swt = -1; break;
        }
        return __swt;
    }

    public boolean isAligned() {
        return this != UNASSIGNED;
    }

    public Vector3fc getAlignedNormal() {
        if (!this.isAligned()) {
            throw new IllegalStateException("Cannot get aligned normal for unassigned facing");
        }
        return ALIGNED_NORMALS[this.ordinal()];
    }

    public int getPackedAlignedNormal() {
        if (!this.isAligned()) {
            throw new IllegalStateException("Cannot get packed aligned normal for unassigned facing");
        }
        return PACKED_ALIGNED_NORMALS[this.ordinal()];
    }

    public static ModelQuadFacing fromNormal(Vector3fc normal) {
        for (EnumFacing face : DirectionUtil.ALL_DIRECTIONS) {
            if (Mth.equal(normal.x(), face.getFrontOffsetX()) && Mth.equal(normal.y(), face.getFrontOffsetY()) && Mth.equal(normal.z(), face.getFrontOffsetZ())) {
                return ModelQuadFacing.fromDirection(face);
            }
        }
        return ModelQuadFacing.UNASSIGNED;
    }


    public static ModelQuadFacing fromNormal(float x, float y, float z) {
        for (EnumFacing face : DirectionUtil.ALL_DIRECTIONS) {
            if (Mth.equal(x, face.getFrontOffsetX()) && Mth.equal(y, face.getFrontOffsetY()) && Mth.equal(z, face.getFrontOffsetZ())) {
                return ModelQuadFacing.fromDirection(face);
            }
        }

        return ModelQuadFacing.UNASSIGNED;
    }

    public static ModelQuadFacing fromPackedNormal(int normal) {
        return fromNormal(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal));
    }

    public static boolean bitmapIsOpposingAligned(int bitmap) {
        return bitmap == OPPOSING_X || bitmap == OPPOSING_Y || bitmap == OPPOSING_Z;
    }

    public static boolean bitmapHasUnassigned(int bitmap) {
        return (bitmap & UNASSIGNED_MASK) != 0;
    }
}

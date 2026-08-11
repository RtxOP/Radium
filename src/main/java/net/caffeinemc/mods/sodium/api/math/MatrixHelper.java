package net.caffeinemc.mods.sodium.api.math;

import net.caffeinemc.mods.sodium.api.util.NormI8;

import org.joml.Math;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Implements optimized utilities for transforming vectors with a given matrix.
 *
 * Note: Brackets must be used carefully in the transform functions to ensure that floating-point errors are
 * the same as those produced by JOML, otherwise Z-fighting will occur.
 */
public class MatrixHelper {
    /**
     * @param mat The transformation matrix to apply to the normal
     * @param skipNormalization Whether normalizing the vector is unnecessary
     * @return The transformed normal vector (in packed format)
     */
    public static int transformNormal(Matrix3f mat, boolean skipNormalization, float x, float y, float z) {
        float nxt = transformNormalX(mat, x, y, z);
        float nyt = transformNormalY(mat, x, y, z);
        float nzt = transformNormalZ(mat, x, y, z);

        if (!skipNormalization) {
            float scalar = Math.invsqrt(Math.fma(nxt, nxt, Math.fma(nyt, nyt, nzt * nzt)));

            nxt *= scalar;
            nyt *= scalar;
            nzt *= scalar;
        }

        return NormI8.pack(nxt, nyt, nzt);
    }

    /**
     * @param mat The transformation matrix to apply to the normal
     * @return The transformed normal vector (in packed format)
     */
    public static int transformSafeNormal(Matrix3f mat, float x, float y, float z) {
        return NormI8.pack(transformNormalX(mat, x, y, z), transformNormalY(mat, x, y, z), transformNormalZ(mat, x, y, z));
    }

    public static int transformNormal(Matrix3f mat, boolean skipNormalization, int norm) {
        float x = NormI8.unpackX(norm);
        float y = NormI8.unpackY(norm);
        float z = NormI8.unpackZ(norm);

        return transformNormal(mat, skipNormalization, x, y, z);
    }

    public static float transformNormalX(Matrix3f mat, float x, float y, float z) {
        return (mat.m00() * x) + ((mat.m10() * y) + (mat.m20() * z));
    }

    public static float transformNormalY(Matrix3f mat, float x, float y, float z) {
        return (mat.m01() * x) + ((mat.m11() * y) + (mat.m21() * z));
    }

    public static float transformNormalZ(Matrix3f mat, float x, float y, float z) {
        return (mat.m02() * x) + ((mat.m12() * y) + (mat.m22() * z));
    }

    public static float transformPositionX(Matrix4f mat, float x, float y, float z) {
        return (mat.m00() * x) + ((mat.m10() * y) + ((mat.m20() * z) + mat.m30()));
    }

    public static float transformPositionY(Matrix4f mat, float x, float y, float z) {
        return (mat.m01() * x) + ((mat.m11() * y) + ((mat.m21() * z) + mat.m31()));
    }

    public static float transformPositionZ(Matrix4f mat, float x, float y, float z) {
        return (mat.m02() * x) + ((mat.m12() * y) + ((mat.m22() * z) + mat.m32()));
    }
}

package net.caffeinemc.mods.sodium.client.util.frustum;

/**
 * Stub interface implemented by {@link gg.sona.radium.mixin.core.MixinBaseFrustum}
 * to add a Sodium-style AABB-vs-frustum test. Returns one of
 * {@link org.joml.FrustumIntersection#OUTSIDE}, {@code INTERSECT}, or {@code INSIDE}.
 */
public interface ExtendedFrustum {
    int radium$intersect(double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ);

    float[][] radium$getPlanes();
}

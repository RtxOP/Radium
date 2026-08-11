package gg.sona.radium.mixin.core;

import net.caffeinemc.mods.sodium.client.util.frustum.ExtendedFrustum;
import net.minecraft.client.renderer.culling.ClippingHelper;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClippingHelper.class)
public abstract class MixinBaseFrustum implements ExtendedFrustum {
    @Shadow
    public float[][] frustum;

    @Shadow
    protected abstract double dot(float[] frustum, double x, double y, double z);

    /**
     * Tests an AABB (axis-aligned bounding box) against this frustum.
     *
     * @param minX minimum x of the box
     * @param minY minimum y of the box
     * @param minZ minimum z of the box
     * @param maxX maximum x of the box
     * @param maxY maximum y of the box
     * @param maxZ maximum z of the box
     * @return {@link FrustumIntersection#OUTSIDE} if the box is completely outside,
     *         {@link FrustumIntersection#INTERSECT} if it intersects,
     *         {@link FrustumIntersection#INSIDE} if it is completely inside
     */
    @Override
    public int radium$intersect(double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ) {
        boolean intersects = false;

        for (int i = 0; i < 6; i++) {
            float[] plane = this.frustum[i];

            // Count how many corners are in front of this plane
            int inCount = 0;
            if (this.dot(plane, minX, minY, minZ) > 0.0) inCount++;
            if (this.dot(plane, maxX, minY, minZ) > 0.0) inCount++;
            if (this.dot(plane, minX, maxY, minZ) > 0.0) inCount++;
            if (this.dot(plane, maxX, maxY, minZ) > 0.0) inCount++;
            if (this.dot(plane, minX, minY, maxZ) > 0.0) inCount++;
            if (this.dot(plane, maxX, minY, maxZ) > 0.0) inCount++;
            if (this.dot(plane, minX, maxY, maxZ) > 0.0) inCount++;
            if (this.dot(plane, maxX, maxY, maxZ) > 0.0) inCount++;

            if (inCount == 0) {
                return FrustumIntersection.OUTSIDE;
            } else if (inCount < 8) {
                intersects = true;
            }
        }

        return intersects ? FrustumIntersection.INTERSECT : FrustumIntersection.INSIDE;
    }

    @Override
    public float[][] radium$getPlanes() {
        return this.frustum;
    }
}

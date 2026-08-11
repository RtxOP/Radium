package gg.sona.radium.mixin.core;

import net.caffeinemc.mods.sodium.client.util.frustum.ExtendedFrustum;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Bridges the vanilla 1.8.9 {@link Frustum} (the camera used for chunk culling)
 * onto the Sodium {@link ExtendedFrustum} contract. The actual AABB-vs-frustum
 * test lives on the backing {@link ClippingHelper} via {@link MixinBaseFrustum};
 * this mixin simply forwards to it.
 */
@Mixin(Frustum.class)
public class MixinCullingCameraView implements ExtendedFrustum {
    @Shadow
    private ClippingHelper clippingHelper;

    @Override
    public int radium$intersect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return ((ExtendedFrustum) this.clippingHelper).radium$intersect(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public float[][] radium$getPlanes() {
        return ((ExtendedFrustum) this.clippingHelper).radium$getPlanes();
    }
}

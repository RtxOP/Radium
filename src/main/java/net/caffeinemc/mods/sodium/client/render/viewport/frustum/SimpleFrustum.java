package net.caffeinemc.mods.sodium.client.render.viewport.frustum;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import org.joml.FrustumIntersection;
import org.joml.Vector4f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * MCP 1.8.9 port: the reference derives its frustum planes from a JOML {@link FrustumIntersection} fed with the
 * camera's projection/model matrices. 1.8.9 has no Camera class, so the caller builds the {@code FrustumIntersection}
 * from the combined P*M matrix of the fixed-function GL camera state (see
 * {@code LevelRendererMixin.setupTerrain}). This deliberately bypasses the vanilla {@code ClippingHelper}, whose
 * notch extraction multiplies the matrices in the transposed order (M*P) and yields a degenerate frustum.
 */
public final class SimpleFrustum implements Frustum {
    private final float nxX, nxY, nxZ, negNxW;
    private final float pxX, pxY, pxZ, negPxW;
    private final float nyX, nyY, nyZ, negNyW;
    private final float pyX, pyY, pyZ, negPyW;
    private final float nzX, nzY, nzZ, negNzW;
    private final float pzX, pzY, pzZ, negPzW;

    private final FrustumIntersection frustum;

    private static final MethodHandle PLANES_GETTER;
    static {
        try {
            Field field = FrustumIntersection.class.getDeclaredField("planes");
            field.setAccessible(true);
            PLANES_GETTER = MethodHandles.lookup().unreflectGetter(field);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to find planes field in JOML", e);
        }
    }

    public SimpleFrustum(FrustumIntersection frustumIntersection) {
        this.frustum = frustumIntersection;
        Vector4f[] planes;
        try {
            planes = (Vector4f[]) PLANES_GETTER.invokeExact(frustumIntersection);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to access planes field in FrustumIntersection", e);
        }

        this.nxX = planes[0].x; this.nxY = planes[0].y; this.nxZ = planes[0].z;
        this.pxX = planes[1].x; this.pxY = planes[1].y; this.pxZ = planes[1].z;
        this.nyX = planes[2].x; this.nyY = planes[2].y; this.nyZ = planes[2].z;
        this.pyX = planes[3].x; this.pyY = planes[3].y; this.pyZ = planes[3].z;
        this.nzX = planes[4].x; this.nzY = planes[4].y; this.nzZ = planes[4].z;
        this.pzX = planes[5].x; this.pzY = planes[5].y; this.pzZ = planes[5].z;

        gg.sona.radium.diag.Diag.throttle("frustum-planes",
                "frustum planes: nx=" + fmt(planes[0]) + " px=" + fmt(planes[1])
                        + " ny=" + fmt(planes[2]) + " py=" + fmt(planes[3])
                        + " nz=" + fmt(planes[4]) + " pz=" + fmt(planes[5]));

        final float size = Viewport.CHUNK_SECTION_PADDED_RADIUS;
        this.negNxW = negW(planes[0], size);
        this.negPxW = negW(planes[1], size);
        this.negNyW = negW(planes[2], size);
        this.negPyW = negW(planes[3], size);
        this.negNzW = negW(planes[4], size);
        this.negPzW = negW(planes[5], size);
    }

    private static String fmt(Vector4f p) {
        return String.format("[%.2f,%.2f,%.2f,%.2f]", p.x, p.y, p.z, p.w);
    }

    private static float negW(Vector4f p, float size) {
        return -(p.w
                + p.x * (p.x < 0 ? -size : size)
                + p.y * (p.y < 0 ? -size : size)
                + p.z * (p.z < 0 ? -size : size));
    }

    @Override
    public boolean testSection(float x, float y, float z) {
        // Skip far plane checks because it has been ensured by searchDistance and isWithinRenderDistance check in OcclusionCuller
        return this.nxX * x + this.nxY * y + this.nxZ * z >= this.negNxW &&
                this.pxX * x + this.pxY * y + this.pxZ * z >= this.negPxW &&
                this.nyX * x + this.nyY * y + this.nyZ * z >= this.negNyW &&
                this.pyX * x + this.pyY * y + this.pyZ * z >= this.negPyW &&
                this.nzX * x + this.nzY * y + this.nzZ * z >= this.negNzW;
    }

    @Override
    public boolean testSectionExpanded(float floatOriginX, float floatOriginY, float floatOriginZ, float extend) {
        float minX = floatOriginX - extend;
        float maxX = floatOriginX + extend;
        float minY = floatOriginY - extend;
        float maxY = floatOriginY + extend;
        float minZ = floatOriginZ - extend;
        float maxZ = floatOriginZ + extend;

        return this.nxX * (this.nxX < 0 ? minX : maxX) + this.nxY * (this.nxY < 0 ? minY : maxY) + this.nxZ * (this.nxZ < 0 ? minZ : maxZ) >= this.negNxW &&
                this.pxX * (this.pxX < 0 ? minX : maxX) + this.pxY * (this.pxY < 0 ? minY : maxY) + this.pxZ * (this.pxZ < 0 ? minZ : maxZ) >= this.negPxW &&
                this.nyX * (this.nyX < 0 ? minX : maxX) + this.nyY * (this.nyY < 0 ? minY : maxY) + this.nyZ * (this.nyZ < 0 ? minZ : maxZ) >= this.negNyW &&
                this.pyX * (this.pyX < 0 ? minX : maxX) + this.pyY * (this.pyY < 0 ? minY : maxY) + this.pyZ * (this.pyZ < 0 ? minZ : maxZ) >= this.negPyW &&
                this.nzX * (this.nzX < 0 ? minX : maxX) + this.nzY * (this.nzY < 0 ? minY : maxY) + this.nzZ * (this.nzZ < 0 ? minZ : maxZ) >= this.negNzW &&
                this.pzX * (this.pzX < 0 ? minX : maxX) + this.pzY * (this.pzY < 0 ? minY : maxY) + this.pzZ * (this.pzZ < 0 ? minZ : maxZ) >= this.negPzW;
    }

    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ) != FrustumIntersection.OUTSIDE;
    }

    @Override
    public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
    }
}

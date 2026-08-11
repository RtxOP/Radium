package dev.vexor.radium.compat.mojang.minecraft.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * MCP 1.8.9 adaptation of the reference FogHelper.
 *
 * <p>The reference reads {@code GlStateManager.FOG.{start,end}} and {@code GameRenderer.fog{R,G,B}}.
 * MCP 1.8.9 keeps the fog color in private fields of {@code EntityRenderer} and pushes all fog state into
 * the fixed-function GL state during {@code setupFog()}, so this shim reads the GL state directly
 * (semantically identical values, updated every frame before terrain rendering).</p>
 */
public class FogHelper {
    public static float getFogEnd() {
        return GL11.glGetFloat(GL11.GL_FOG_END);
    }

    public static float getFogStart() {
        return GL11.glGetFloat(GL11.GL_FOG_START);
    }

    public static float[] getFogColor() {
        // LWJGL2's BufferChecks requires >= 16 remaining elements for glGetFloat (LWJGL3 accepts 4),
        // so allocate 16 and read only the 4 GL_FOG_COLOR components.
        FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_FOG_COLOR, buf);
        return new float[] { buf.get(0), buf.get(1), buf.get(2), buf.get(3) };
    }
}

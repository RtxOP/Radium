package net.caffeinemc.mods.sodium.client.gl.shader.uniform;

import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GlUniformMatrix4f extends GlUniform<Matrix4fc>  {
    public GlUniformMatrix4f(int index) {
        super(index);
    }

    @Override
    public void set(Matrix4fc value) {
        // LWJGL2 has no MemoryStack; allocate a small direct buffer for the 16 floats.
        FloatBuffer buf = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        value.get(buf);

        // Note: LWJGL2 names the FloatBuffer form glUniformMatrix4 (glUniformMatrix4fv is native-only).
        GL20.glUniformMatrix4(this.index, false, buf);
    }
}

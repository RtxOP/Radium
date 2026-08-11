package net.caffeinemc.mods.sodium.api.vertex.attributes.common;

import org.joml.Vector2f;
import java.nio.ByteBuffer;

/**
 * LWJGL2 port: {@code MemoryUtil.memPutFloat} becomes {@code ByteBuffer.putFloat(offset, v)}.
 */
public class TextureAttribute {
    public static void put(ByteBuffer buffer, int offset, Vector2f vec) {
        put(buffer, offset, vec.x(), vec.y());
    }

    public static void put(ByteBuffer buffer, int offset, float u, float v) {
        buffer.putFloat(offset + 0, u);
        buffer.putFloat(offset + 4, v);
    }

    public static Vector2f get(ByteBuffer buffer, int offset) {
        return new Vector2f(getU(buffer, offset), getV(buffer, offset));
    }

    public static float getU(ByteBuffer buffer, int offset) {
        return buffer.getFloat(offset + 0);
    }

    public static float getV(ByteBuffer buffer, int offset) {
        return buffer.getFloat(offset + 4);
    }
}

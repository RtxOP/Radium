package net.caffeinemc.mods.sodium.api.vertex.attributes.common;

import java.nio.ByteBuffer;

/**
 * LWJGL2 port: the reference uses {@code MemoryUtil.memPutInt(ptr, ...)}; here the
 * attribute is written through a {@link ByteBuffer} at an absolute byte offset.
 */
public class LightAttribute {
    public static void set(ByteBuffer buffer, int offset, int light) {
        buffer.putInt(offset, light);
    }

    public static int get(ByteBuffer buffer, int offset) {
        return buffer.getInt(offset);
    }
}

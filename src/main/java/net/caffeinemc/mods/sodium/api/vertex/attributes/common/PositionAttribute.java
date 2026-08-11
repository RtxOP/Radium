package net.caffeinemc.mods.sodium.api.vertex.attributes.common;

import java.nio.ByteBuffer;

/**
 * LWJGL2 port: {@code MemoryUtil.memPutFloat(long, float)} becomes
 * {@code ByteBuffer.putFloat(int, float)} at an absolute offset.
 */
public class PositionAttribute {
    public static void put(ByteBuffer buffer, int offset, float x, float y, float z) {
        buffer.putFloat(offset + 0, x);
        buffer.putFloat(offset + 4, y);
        buffer.putFloat(offset + 8, z);
    }

    public static float getX(ByteBuffer buffer, int offset) {
        return buffer.getFloat(offset + 0);
    }

    public static float getY(ByteBuffer buffer, int offset) {
        return buffer.getFloat(offset + 4);
    }

    public static float getZ(ByteBuffer buffer, int offset) {
        return buffer.getFloat(offset + 8);
    }
}

package net.caffeinemc.mods.sodium.api.vertex.serializer;

import java.nio.ByteBuffer;

/**
 * LWJGL2 port divergence: the reference signature is
 * {@code void serialize(long srcBuffer, long dstBuffer, int count)} using {@code MemoryUtil};
 * this port serializes between two {@link ByteBuffer}s at absolute byte offsets.
 */
public interface VertexSerializer {
    void serialize(ByteBuffer srcBuffer, ByteBuffer dstBuffer, int count);
}

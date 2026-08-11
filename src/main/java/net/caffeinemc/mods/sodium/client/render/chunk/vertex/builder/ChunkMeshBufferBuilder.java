package net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder;

import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Grows a {@link ByteBuffer} of encoded chunk-vertex data.
 *
 * <p>Port divergence from the reference: the LWJGL2 port has no {@code org.lwjgl.system.MemoryUtil}
 * (no {@code memRealloc}/{@code memSlice}/{@code memFree}), so growth is a plain allocate+copy into
 * a new native-order direct buffer.</p>
 */
public class ChunkMeshBufferBuilder {
    private final ChunkVertexEncoder encoder;
    private final int stride;

    private final int initialCapacity;

    private ByteBuffer buffer;
    private int vertexCount;
    private int vertexCapacity;

    private int sectionIndex;

    public ChunkMeshBufferBuilder(ChunkVertexType vertexType, int initialCapacity) {
        this.encoder = vertexType.getEncoder();
        this.stride = vertexType.getVertexFormat().getStride();

        this.buffer = null;

        this.vertexCapacity = initialCapacity;
        this.initialCapacity = initialCapacity;
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        this.push(vertices, material.bits());
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, int materialBits) {
        if (vertices.length != 4) {
            throw new IllegalArgumentException("Only quad primitives (with 4 vertices) can be pushed");
        }

        this.ensureCapacity(4);

        this.encoder.write(this.buffer, this.vertexCount * this.stride,
                materialBits, vertices, this.sectionIndex);
        this.vertexCount += 4;
    }

    public void writeExternal(ByteBuffer buffer, int position, ChunkVertexEncoder.Vertex[] vertices, Material material) {
        this.encoder.write(buffer, position * this.stride,
                material.bits(), vertices, this.sectionIndex);
    }

    private void ensureCapacity(int vertexCount) {
        if (this.vertexCount + vertexCount >= this.vertexCapacity) {
            this.grow(vertexCount);
        }
    }

    private void grow(int vertexCount) {
        this.reallocate(
                Math.max(this.vertexCapacity * 2, this.vertexCapacity + vertexCount)
        );
    }

    private void reallocate(int vertexCount) {
        int byteCount = vertexCount * this.stride;

        ByteBuffer oldBuffer = this.buffer;
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());

        if (oldBuffer != null) {
            ByteBuffer src = oldBuffer.duplicate();
            src.position(0);
            src.limit(Math.min(src.limit(), newBuffer.capacity()));
            newBuffer.put(src);
        }

        this.buffer = newBuffer;
        this.vertexCapacity = vertexCount;
    }

    public void start(int sectionIndex) {
        this.vertexCount = 0;
        this.sectionIndex = sectionIndex;

        this.reallocate(this.initialCapacity);
    }

    public void destroy() {
        this.buffer = null;
    }

    public boolean isEmpty() {
        return this.vertexCount == 0;
    }

    public ByteBuffer slice() {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        ByteBuffer slice = this.buffer.duplicate();
        // JDK 8 does not preserve byte order across duplicate() (it resets to BIG_ENDIAN);
        // restore the builder's order so absolute getInt()/decode reads stay consistent.
        slice.order(this.buffer.order());
        slice.position(0);
        slice.limit(this.stride * this.vertexCount);

        return slice;
    }

    public int count() {
        return this.vertexCount;
    }
}

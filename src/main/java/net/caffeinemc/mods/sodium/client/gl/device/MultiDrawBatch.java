package net.caffeinemc.mods.sodium.client.gl.device;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Provides a fixed-size queue for building a draw-command list usable with
 * {@link org.lwjgl.opengl.GL32#glDrawElementsBaseVertex(int, int, int, long, int)} .
 *
 * <p>The reference implementation stores these arrays in raw native memory (via
 * {@code MemoryUtil.nmemAlignedAlloc}), because LWJGL3's {@code glMultiDrawElementsBaseVertex}
 * consumes raw pointers. LWJGL 2 has no multi-draw call, so the port iterates the batch in
 * {@code GLRenderDevice} and calls {@code glDrawElementsBaseVertex} once per entry. Plain direct
 * buffers are used for the backing storage, which the garbage collector reclaims.</p>
 */
public final class MultiDrawBatch {
    public final IntBuffer elementCounts;   // was pElementCount (capacity * Integer.BYTES)
    public final IntBuffer baseVertices;    // was pBaseVertex
    public final LongBuffer elementOffsets; // was pElementPointer (byte offsets; capacity * Pointer.POINTER_SIZE)

    public int size;
    public boolean isFilled;

    public MultiDrawBatch(int capacity) {
        this.elementCounts = ByteBuffer.allocateDirect(capacity * Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer();
        this.baseVertices = ByteBuffer.allocateDirect(capacity * Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer();
        this.elementOffsets = ByteBuffer.allocateDirect(capacity * Long.BYTES).order(ByteOrder.nativeOrder()).asLongBuffer();
    }

    public void clear() {
        this.size = 0;
        this.isFilled = false;
    }

    public void delete() {
        // Backing memory is managed by the garbage collector.
    }

    public boolean isEmpty() {
        return this.size <= 0;
    }

    public int getIndexBufferSize() {
        int elements = 0;

        for (int index = 0; index < this.size; index++) {
            elements = Math.max(elements, this.elementCounts.get(index));
        }

        return elements;
    }
}

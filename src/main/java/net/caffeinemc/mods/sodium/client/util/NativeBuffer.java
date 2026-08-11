package net.caffeinemc.mods.sodium.client.util;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A native-memory-backed buffer with leak-reclaim tracking.
 *
 * <p>The reference allocates raw native memory through {@code MemoryUtil.nmemAlloc}
 * and wraps it back into a {@link ByteBuffer} on demand. LWJGL 2 (MC 1.8.9) has no
 * {@code nmemAlloc}/{@code memByteBuffer} API, so the port backs the buffer with a
 * direct {@link ByteBuffer} instead — the reclaim/leak-reporting structure is kept
 * unchanged.</p>
 */
public class NativeBuffer {
    private static final Logger LOGGER = LogManager.getLogger(NativeBuffer.class);

    private static final ReferenceQueue<NativeBuffer> RECLAIM_QUEUE = new ReferenceQueue<>();
    // Reference2Reference fastutil semantics are identity-based, exactly what
    // IdentityHashMap provides (synchronized, like fastutil's synchronize()).
    private static final Map<Reference<NativeBuffer>, BufferReference> ACTIVE_BUFFERS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private static long ALLOCATED = 0L;

    private final BufferReference ref;

    public NativeBuffer(int capacity) {
        this.ref = allocate(capacity);

        ACTIVE_BUFFERS.put(new PhantomReference<>(this, RECLAIM_QUEUE), this.ref);
    }

    public static NativeBuffer copy(ByteBuffer src) {
        NativeBuffer dst = new NativeBuffer(src.remaining());

        ByteBuffer target = dst.getDirectBuffer();
        target.put(src);
        target.rewind();

        return dst;
    }

    public ByteBuffer getDirectBuffer() {
        this.ref.checkFreed();

        return this.ref.buffer.duplicate();
    }

    public void free() {
        deallocate(this.ref);
    }

    public int getLength() {
        return this.ref.length;
    }

    public static void reclaim(boolean forceGc) {
        if (forceGc) {
            System.gc();
        }

        Reference<? extends NativeBuffer> ref;

        while ((ref = RECLAIM_QUEUE.poll()) != null) {
            BufferReference buf = ACTIVE_BUFFERS.remove(ref);

            if (buf.freed) {
                continue;
            }

            deallocate(buf);

            if (buf.allocationSite != null) {
                LOGGER.warn("Reclaimed {} bytes that were leaked from allocation site:\n{}",
                        buf.length,
                        Arrays.stream(buf.allocationSite)
                                .map(StackTraceElement::toString)
                                .collect(Collectors.joining("\n")));
            } else {
                LOGGER.warn("Reclaimed {} bytes that were leaked from an unknown location (logging is disabled)",
                        buf.length);
            }
        }
    }

    public static long getTotalAllocated() {
        return ALLOCATED;
    }

    private static StackTraceElement[] getStackTrace() {
        return SodiumClientMod.options().advanced.enableMemoryTracing ? Thread.currentThread()
                .getStackTrace() : null;
    }

    private static final int MAX_ALLOCATION_ATTEMPTS = 3;

    private static BufferReference allocate(int bytes) {
        ByteBuffer buffer = null;
        int attempts = 0;

        while (++attempts <= MAX_ALLOCATION_ATTEMPTS) {
            try {
                buffer = ByteBuffer.allocateDirect(bytes);
                break;
            } catch (OutOfMemoryError error) {
                LOGGER.error("EMERGENCY: Tried to allocate {} bytes but the allocator reports failure", bytes);
                LOGGER.error("EMERGENCY: ... Attempting to force a garbage collection cycle (attempt {}/{})", attempts, MAX_ALLOCATION_ATTEMPTS);

                // If memory allocation fails, force a garbage collection
                reclaim(true);
            }
        }

        if (buffer == null) {
            throw new OutOfMemoryError(String.format("Couldn't allocate %s bytes after %s attempts", bytes, attempts));
        }

        StackTraceElement[] stackTrace = getStackTrace();

        BufferReference ref = new BufferReference(buffer, bytes, stackTrace);
        ALLOCATED += ref.length;

        return ref;
    }

    private static void deallocate(BufferReference ref) {
        ref.checkFreed();
        ref.freed = true;

        // Let the garbage collector reclaim the direct buffer's backing memory.
        ref.buffer = null;

        ALLOCATED -= ref.length;
    }

    private static class BufferReference {
        public ByteBuffer buffer;
        public final int length;

        public final StackTraceElement[] allocationSite;

        public boolean freed;

        private BufferReference(ByteBuffer buffer, int length, StackTraceElement[] allocationSite) {
            this.buffer = buffer;
            this.length = length;
            this.allocationSite = allocationSite;
        }

        private void checkFreed() {
            if (this.freed) {
                throw new IllegalStateException("Buffer has been deleted");
            }
        }
    }
}

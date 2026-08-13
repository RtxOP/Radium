package dev.vexor.radium.compat.lwjgl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * High-performance, zero-GC off-heap memory manager and LWJGL2 replacement for {@code org.lwjgl.system.MemoryUtil}.
 * Backed directly by {@code sun.misc.Unsafe} and raw {@code DirectByteBuffer} pointer reflection on JRE 8.
 */
public final class MemoryUtil {
    private static final sun.misc.Unsafe UNSAFE = getUnsafe();
    private static final Constructor<?> DIRECT_BYTE_BUFFER_CTOR = getDirectByteBufferConstructor();
    private static final long BUFFER_ADDRESS_OFFSET = getBufferAddressOffset();

    private static sun.misc.Unsafe getUnsafe() {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Unable to obtain sun.misc.Unsafe", e);
        }
    }

    private static Constructor<?> getDirectByteBufferConstructor() {
        try {
            Class<?> clazz = Class.forName("java.nio.DirectByteBuffer");
            Constructor<?> ctor = clazz.getDeclaredConstructor(long.class, int.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (Exception e) {
            throw new RuntimeException("Unable to obtain DirectByteBuffer(long, int) constructor", e);
        }
    }

    private static long getBufferAddressOffset() {
        try {
            Field f = Buffer.class.getDeclaredField("address");
            return UNSAFE.objectFieldOffset(f);
        } catch (Exception e) {
            throw new RuntimeException("Unable to obtain Buffer.address field offset", e);
        }
    }

    private MemoryUtil() {
    }

    /**
     * Allocates {@code bytes} of unmanaged off-heap native memory.
     */
    public static long nmemAlloc(long bytes) {
        return UNSAFE.allocateMemory(bytes);
    }

    /**
     * Reallocates unmanaged off-heap native memory at {@code pointer} to {@code bytes}.
     */
    public static long nmemRealloc(long pointer, long bytes) {
        if (pointer == 0L) {
            return nmemAlloc(bytes);
        }
        if (bytes == 0L) {
            nmemFree(pointer);
            return 0L;
        }
        return UNSAFE.reallocateMemory(pointer, bytes);
    }

    /**
     * Frees unmanaged off-heap native memory at {@code pointer}.
     */
    public static void nmemFree(long pointer) {
        if (pointer != 0L) {
            UNSAFE.freeMemory(pointer);
        }
    }

    /**
     * Allocates {@code bytes} bytes aligned to {@code alignment} (power of two, at least 8).
     */
    public static long nmemAlignedAlloc(long alignment, long bytes) {
        if (alignment < 8 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("alignment must be a power of two >= 8");
        }
        long header = 8;
        long base = UNSAFE.allocateMemory(bytes + header + alignment - 1);
        long aligned = (base + header + alignment - 1) & ~(alignment - 1);
        UNSAFE.putLong(aligned - header, base);
        return aligned;
    }

    public static void nmemAlignedFree(long pointer) {
        if (pointer != 0L) {
            UNSAFE.freeMemory(UNSAFE.getLong(pointer - 8));
        }
    }

    /**
     * Allocates a native-order DirectByteBuffer wrapping {@code size} bytes of unmanaged off-heap memory.
     */
    public static ByteBuffer memAlloc(int size) {
        long address = nmemAlloc(size);
        return wrap(address, size);
    }

    /**
     * Reallocates the unmanaged backing memory of {@code buffer} to {@code newSize}.
     */
    public static ByteBuffer memRealloc(ByteBuffer buffer, int newSize) {
        if (buffer == null) {
            return memAlloc(newSize);
        }
        long oldAddress = memAddress(buffer);
        long newAddress = nmemRealloc(oldAddress, newSize);
        return wrap(newAddress, newSize);
    }

    /**
     * Frees the unmanaged backing memory of {@code buffer}.
     */
    public static void memFree(ByteBuffer buffer) {
        if (buffer != null && buffer.isDirect()) {
            nmemFree(memAddress(buffer));
        }
    }

    /**
     * Creates a slice of {@code buffer} from {@code offset} with length {@code length} without copying data.
     */
    public static ByteBuffer memSlice(ByteBuffer buffer, int offset, int length) {
        if (buffer == null) {
            throw new NullPointerException("buffer cannot be null");
        }
        long address = memAddress(buffer) + offset;
        return wrap(address, length);
    }

    /**
     * Returns the 64-bit absolute native memory address of {@code buffer}.
     */
    public static long memAddress(Buffer buffer) {
        return UNSAFE.getLong(buffer, BUFFER_ADDRESS_OFFSET);
    }

    /**
     * Returns the 64-bit absolute native memory address of {@code buffer} plus {@code offset}.
     */
    public static long memAddress(Buffer buffer, int offset) {
        return memAddress(buffer) + offset;
    }

    /**
     * Wraps a raw native pointer and capacity in a {@link ByteBuffer} with native byte order.
     */
    public static ByteBuffer wrap(long address, int capacity) {
        try {
            ByteBuffer buffer = (ByteBuffer) DIRECT_BYTE_BUFFER_CTOR.newInstance(address, capacity);
            buffer.order(ByteOrder.nativeOrder());
            return buffer;
        } catch (Exception e) {
            throw new RuntimeException("Failed to wrap native address in ByteBuffer", e);
        }
    }

    public static void memCopy(long src, long dst, long bytes) {
        UNSAFE.copyMemory(src, dst, bytes);
    }

    public static void memCopy(ByteBuffer src, ByteBuffer dst, int bytes) {
        UNSAFE.copyMemory(memAddress(src) + src.position(), memAddress(dst) + dst.position(), bytes);
    }

    public static void memSet(long ptr, int value, long bytes) {
        UNSAFE.setMemory(ptr, bytes, (byte) value);
    }

    public static void memSet(ByteBuffer buffer, int value, int bytes) {
        UNSAFE.setMemory(memAddress(buffer) + buffer.position(), bytes, (byte) value);
    }

    public static byte memGetByte(long ptr) {
        return UNSAFE.getByte(ptr);
    }

    public static void memPutByte(long ptr, byte value) {
        UNSAFE.putByte(ptr, value);
    }

    public static short memGetShort(long ptr) {
        return UNSAFE.getShort(ptr);
    }

    public static void memPutShort(long ptr, short value) {
        UNSAFE.putShort(ptr, value);
    }

    public static int memGetInt(long ptr) {
        return UNSAFE.getInt(ptr);
    }

    public static void memPutInt(long ptr, int value) {
        UNSAFE.putInt(ptr, value);
    }

    public static float memGetFloat(long ptr) {
        return UNSAFE.getFloat(ptr);
    }

    public static void memPutFloat(long ptr, float value) {
        UNSAFE.putFloat(ptr, value);
    }

    public static long memGetLong(long ptr) {
        return UNSAFE.getLong(ptr);
    }

    public static void memPutLong(long ptr, long value) {
        UNSAFE.putLong(ptr, value);
    }
}

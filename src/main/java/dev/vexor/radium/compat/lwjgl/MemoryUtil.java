package dev.vexor.radium.compat.lwjgl;

import java.lang.reflect.Field;

/**
 * LWJGL2-compatible replacement for the small subset of {@code org.lwjgl.system.MemoryUtil} (LWJGL3) used by the
 * chunk renderer. LWJGL 2.9.4 has no raw-address memory intrinsics, so this is backed by {@code sun.misc.Unsafe},
 * which is available on the game's JRE 8.
 *
 * <p>Port divergence (documented): allocation is done with an alignment header so the caller-visible pointer stays
 * aligned; {@link #nmemAlignedFree} reads the original base pointer from the 8 bytes before the aligned pointer.</p>
 */
public final class MemoryUtil {
    private static final sun.misc.Unsafe UNSAFE = getUnsafe();

    private static sun.misc.Unsafe getUnsafe() {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Unable to obtain sun.misc.Unsafe", e);
        }
    }

    private MemoryUtil() {
    }

    /** Allocates {@code bytes} bytes aligned to {@code alignment} (power of two, at least 8). */
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
        UNSAFE.freeMemory(UNSAFE.getLong(pointer - 8));
    }

    public static void memSet(long ptr, int value, long bytes) {
        UNSAFE.setMemory(ptr, bytes, (byte) value);
    }

    public static byte memGetByte(long ptr) {
        return UNSAFE.getByte(ptr);
    }

    public static void memPutByte(long ptr, byte value) {
        UNSAFE.putByte(ptr, value);
    }

    public static int memGetInt(long ptr) {
        return UNSAFE.getInt(ptr);
    }

    public static void memPutInt(long ptr, int value) {
        UNSAFE.putInt(ptr, value);
    }

    public static long memGetLong(long ptr) {
        return UNSAFE.getLong(ptr);
    }

    public static void memPutLong(long ptr, long value) {
        UNSAFE.putLong(ptr, value);
    }
}

package net.caffeinemc.mods.sodium.api.util;

public interface ColorU8 {
    int COMPONENT_BITS = 8;
    int COMPONENT_MASK = (1 << COMPONENT_BITS) - 1;
    float COMPONENT_RANGE = (float) COMPONENT_MASK;
    float COMPONENT_RANGE_INVERSE = 1.0f / COMPONENT_RANGE;

    static int normalizedFloatToByte(float value) {
        return (int) (value * COMPONENT_RANGE) & COMPONENT_MASK;
    }

    static float byteToNormalizedFloat(int value) {
        return (float) value * COMPONENT_RANGE_INVERSE;
    }
}

package net.caffeinemc.mods.sodium.api.vertex.format;

/** Stub: allocates a unique integer id for each VertexFormat at runtime. */
public class VertexFormatRegistry {
    private static final VertexFormatRegistry INSTANCE = new VertexFormatRegistry();

    public static VertexFormatRegistry instance() {
        return INSTANCE;
    }

    public int allocateGlobalId(Object format) {
        return 0;
    }
}

package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

public final class MeshResultSize implements Average1DEstimator.Value<MeshResultSize.SectionCategory> {
    public static final long NO_DATA = -1L;
    private final SectionCategory category;
    private final long resultSize;

    public MeshResultSize(SectionCategory category, long resultSize) {
        this.category = category;
        this.resultSize = resultSize;
    }

    public enum SectionCategory {
        LOW,
        UNDERGROUND,
        WATER_LEVEL,
        SURFACE,
        HIGH;

        public static SectionCategory forSection(RenderSection section, int seaLevelChunk) {
            int sectionY = section.getChunkY();
            if (sectionY == seaLevelChunk) return WATER_LEVEL;
            if (sectionY < seaLevelChunk - 4) return LOW;
            if (sectionY < seaLevelChunk) return UNDERGROUND;
            if (sectionY < seaLevelChunk + 3) return SURFACE;
            return HIGH;
        }
    }

    @Override
    public SectionCategory category() {
        return this.category;
    }

    @Override
    public long value() {
        return this.resultSize;
    }
}

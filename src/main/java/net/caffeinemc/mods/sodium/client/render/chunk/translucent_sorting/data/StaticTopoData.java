package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;

import java.nio.IntBuffer;
import java.util.function.IntConsumer;

/**
 * Static topo acyclic sorting uses the topo sorting algorithm but only if it's
 * possible to sort without dynamic triggering, meaning the sort order never
 * needs to change.
 */
public class StaticTopoData extends PresentTranslucentData {
    private Sorter sorterOnce;

    StaticTopoData(SectionPos sectionPos, int inputQuadCount) {
        super(sectionPos, inputQuadCount);    }

    @Override
    public SortType getSortType() {
        return SortType.STATIC_TOPO;
    }

    @Override
    public Sorter getSorter() {
        Sorter sorter = this.sorterOnce;
        if (sorter == null) {
            throw new IllegalStateException("Sorter already used!");
        }
        this.sorterOnce = null;
        return sorter;
    }

    private static class QuadIndexConsumerIntoBuffer implements IntConsumer  {
    private final IntBuffer buffer;
    public QuadIndexConsumerIntoBuffer(IntBuffer buffer) {
        this.buffer = buffer;
        }

    public IntBuffer buffer() {
        return this.buffer;
    }

        @Override
        public void accept(int value) {
            TranslucentData.writeQuadVertexIndexes(this.buffer, value);
        }
    
}

    public static StaticTopoData fromMesh(TQuad[] quads, SectionPos sectionPos, boolean failOnIntersection) {
        StaticSorter sorter = new StaticSorter(quads.length);
        QuadIndexConsumerIntoBuffer indexWriter = new QuadIndexConsumerIntoBuffer(sorter.getIntBuffer());

        if (!TopoGraphSorting.topoGraphSort(indexWriter, quads, null, null, failOnIntersection)) {
            sorter.getIndexBuffer().free();
            return null;
        }

        StaticTopoData staticTopoData = new StaticTopoData(sectionPos, quads.length);
        staticTopoData.sorterOnce = sorter;
        return staticTopoData;
    }
}

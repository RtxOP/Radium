package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;

public class DeferredTaskList extends LongHeapPriorityQueue {
    private final int baseOffsetX;
    private final int baseOffsetZ;

    public static DeferredTaskList createHeapCopyOf(LongCollection copyFrom, int baseOffsetX, int baseOffsetZ) {
        return new DeferredTaskList(new LongArrayList(copyFrom), baseOffsetX, baseOffsetZ);
    }

    private DeferredTaskList(LongArrayList copyFrom, int baseOffsetX, int baseOffsetZ) {
        super(copyFrom.elements(), copyFrom.size());
        this.baseOffsetX = baseOffsetX;
        this.baseOffsetZ = baseOffsetZ;
    }

    public long dequeueNextSectionPos() {
        long encoded = this.dequeueLong();

        int localX = (int) (encoded >>> 20) & 0b1111111111;
        int localY = (int) (encoded >>> 10) & 0b1111111111;
        int localZ = (int) (encoded & 0b1111111111);

        int globalX = localX + this.baseOffsetX;
        int globalY = localY + TaskCollectingTree.SECTION_Y_MIN;
        int globalZ = localZ + this.baseOffsetZ;

        return SectionPos.asLong(globalX, globalY, globalZ);
    }
}
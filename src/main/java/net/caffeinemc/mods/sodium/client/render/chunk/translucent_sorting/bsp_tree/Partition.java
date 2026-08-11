package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Models a partition of the space into a set of quads that lie inside or on the
 * plane with the specified distance. If the distance is -1 this is the "end"
 * partition after the last partition plane.
 */
class Partition {
    private final float distance;
    private final IntArrayList quadsBefore;
    private final IntArrayList quadsOn;
    public Partition(float distance, IntArrayList quadsBefore, IntArrayList quadsOn) {
        this.distance = distance;
        this.quadsBefore = quadsBefore;
        this.quadsOn = quadsOn;
        }

    public float distance() {
        return this.distance;
    }

    public IntArrayList quadsBefore() {
        return this.quadsBefore;
    }

    public IntArrayList quadsOn() {
        return this.quadsOn;
    }


}

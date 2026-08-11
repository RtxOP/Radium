package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;
import java.util.Arrays;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.CoordinateSectionVisitor;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public abstract class AbstractTraversableMultiForest<T extends TraversableTree> extends BaseMultiForest<T> implements TraversableForest<T> {
    public AbstractTraversableMultiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
        super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
    }

    @Override
    public void prepareForTraversal() {
        for (T tree : this.trees) {
            if (tree != null) {
                tree.prepareForTraversal();
            }
        }
    }

    @Override
    public void traverse(CoordinateSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        SectionPos cameraPos = viewport.getChunkCoord();
        int cameraSectionX = cameraPos.getX();
        int cameraSectionY = cameraPos.getY();
        int cameraSectionZ = cameraPos.getZ();

        // sort the trees by distance from the camera by sorting a packed index array.
        int[] items = new int[this.trees.length];
        for (int i = 0; i < this.trees.length; i++) {
            T tree = this.trees[i];
            if (tree != null) {
                int deltaX = Math.abs(tree.offsetX + 32 - cameraSectionX);
                int deltaY = Math.abs(tree.offsetY + 32 - cameraSectionY);
                int deltaZ = Math.abs(tree.offsetZ + 32 - cameraSectionZ);
                items[i] = (deltaX + deltaY + deltaZ + 1) << 16 | i;
            }
        }

        // fastutil's IntArrays.unstableSort -> JDK's Arrays.sort (stable, same ascending result)
        Arrays.sort(items);

        // traverse in sorted front-to-back order for correct render order
        for (int item : items) {
            if (item == 0) {
                continue;
            }
            T tree = this.trees[item & 0xFFFF];
            if (tree != null) {
                tree.traverse(visitor, viewport, distanceLimit, this.buildDistance);
            }
        }
    }
}

package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import dev.vexor.radium.compat.mojang.minecraft.math.SectionPos;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.CoordinateSectionVisitor;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * JDK 8 port: fastutil's Long2ReferenceLinkedOpenHashMap -> {@link HashMap}, ReferenceArrayList -> ArrayList,
 * ReferenceArrayList.unstableSort -> ArrayList.sort (same ascending order by sort key).
 */
public class RemovableMultiForest implements RemovableForest<RemovableTree> {
    private final Map<Long, RemovableTree> trees;
    private final ArrayList<RemovableTree> treeSortList = new ArrayList<RemovableTree>();
    private RemovableTree lastTree;

    // the removable tree separately tracks if it needs to prepared for traversal because it's not just built once, prepared, and then traversed. Since it can receive updates, it needs to be prepared for traversal again and to avoid unnecessary preparation, it tracks whether it's ready.
    private boolean treesAreReady = true;

    public RemovableMultiForest(float buildDistance) {
        this.trees = new HashMap<Long, RemovableTree>(getCapacity(buildDistance));
    }

    private static int getCapacity(float buildDistance) {
        int forestDim = BaseMultiForest.forestDimFromBuildDistance(buildDistance) + 1;
        return forestDim * forestDim * forestDim;
    }

    public void ensureCapacity(float buildDistance) {
        // HashMap grows automatically; no-op equivalent of fastutil's ensureCapacity.
    }

    @Override
    public void prepareForTraversal() {
        if (this.treesAreReady) {
            return;
        }

        Iterator<RemovableTree> it = this.trees.values().iterator();
        while (it.hasNext()) {
            RemovableTree tree = it.next();
            tree.prepareForTraversal();
            if (tree.isEmpty()) {
                it.remove();
                if (this.lastTree == tree) {
                    this.lastTree = null;
                }
            }
        }

        this.treesAreReady = true;
    }

    @Override
    public int countSections() {
        int count = 0;
        for (RemovableTree tree : this.trees.values()) {
            count += AbstractTraversableBiForest.countTreeSections(tree);
        }
        return count;
    }

    @Override
    public void traverse(CoordinateSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        CameraTransform transform = viewport.getTransform();
        int cameraSectionX = transform.intX >> 4;
        int cameraSectionY = transform.intY >> 4;
        int cameraSectionZ = transform.intZ >> 4;

        // sort the trees by distance from the camera by sorting a packed index array.
        this.treeSortList.clear();
        this.treeSortList.addAll(this.trees.values());
        for (RemovableTree tree : this.treeSortList) {
            tree.updateSortKeyFor(cameraSectionX, cameraSectionY, cameraSectionZ);
        }

        this.treeSortList.sort(Comparator.comparingInt(RemovableTree::getSortKey));

        // traverse in sorted front-to-back order for correct render order
        for (RemovableTree tree : this.treeSortList) {
            // disable distance test in traversal because we don't use it here
            tree.traverse(visitor, viewport, 0, 0);
        }
    }

    @Override
    public boolean add(int x, int y, int z, TreeAddMethod<RemovableTree> addMethod) {
        this.treesAreReady = false;

        if (this.lastTree != null) {
            int result = addMethod.add(this.lastTree, x, y, z);
            if (result != Tree.OUT_OF_BOUNDS) {
                return result == Tree.NOT_PRESENT;
            }
        }

        // get the tree coordinate by dividing by 64
        int treeX = x >> 6;
        int treeY = y >> 6;
        int treeZ = z >> 6;

        long treeKey = SectionPos.asLong(treeX, treeY, treeZ);
        RemovableTree tree = this.trees.get(treeKey);

        if (tree == null) {
            int treeOffsetX = treeX << 6;
            int treeOffsetY = treeY << 6;
            int treeOffsetZ = treeZ << 6;
            tree = new RemovableTree(treeOffsetX, treeOffsetY, treeOffsetZ);
            this.trees.put(treeKey, tree);
        }

        int result = addMethod.add(tree, x, y, z);
        tree.add(x, y, z);
        this.lastTree = tree;
        return result == Tree.NOT_PRESENT;
    }

    public void remove(int x, int y, int z) {
        this.treesAreReady = false;

        if (this.lastTree != null && this.lastTree.remove(x, y, z)) {
            return;
        }

        // get the tree coordinate by dividing by 64
        int treeX = x >> 6;
        int treeY = y >> 6;
        int treeZ = z >> 6;

        long treeKey = SectionPos.asLong(treeX, treeY, treeZ);
        RemovableTree tree = this.trees.get(treeKey);

        if (tree == null) {
            return;
        }

        tree.remove(x, y, z);

        this.lastTree = tree;
    }

    public void remove(RenderSection section) {
        this.remove(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    public void clear() {
        this.trees.clear();
        this.treeSortList.clear();
        this.lastTree = null;
        this.treesAreReady = true;
    }

    @Override
    public int getPresence(int x, int y, int z) {
        // unused operation on removable trees
        throw new UnsupportedOperationException("Not implemented");
    }
}

package net.caffeinemc.mods.sodium.client.render.chunk.tree;

public abstract class BaseMultiForest<T extends Tree> extends BaseForest<T> {
    protected final T[] trees;
    protected final int forestDim;

    protected T lastTree;

    public BaseMultiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
        super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);

        this.forestDim = forestDimFromBuildDistance(buildDistance);
        this.trees = this.makeTrees(this.forestDim * this.forestDim * this.forestDim);
    }

    public static int forestDimFromBuildDistance(float buildDistance) {
        // / 16 (block to chunk) * 2 (radius to diameter) + 1 (center chunk) / 64 (chunks per tree)
        return (int) Math.ceil((buildDistance / 8.0 + 1) / 64.0);
    }

    protected int getTreeIndex(int localX, int localY, int localZ) {
        int treeX = localX >> 6;
        int treeY = localY >> 6;
        int treeZ = localZ >> 6;

        if (treeX < 0 || treeX >= this.forestDim ||
                treeY < 0 || treeY >= this.forestDim ||
                treeZ < 0 || treeZ >= this.forestDim) {
            return Tree.OUT_OF_BOUNDS;
        }

        return treeX + (treeZ * this.forestDim + treeY) * this.forestDim;
    }

    @Override
    public boolean add(int x, int y, int z, TreeAddMethod<T> addMethod) {
        if (this.lastTree != null) {
            int result = addMethod.add(this.lastTree, x, y, z);
            if (result != Tree.OUT_OF_BOUNDS) {
                return result == Tree.NOT_PRESENT;
            }
        }

        int localX = x - this.baseOffsetX;
        int localY = y - this.baseOffsetY;
        int localZ = z - this.baseOffsetZ;

        int treeIndex = this.getTreeIndex(localX, localY, localZ);
        if (treeIndex == Tree.OUT_OF_BOUNDS) {
            return false;
        }

        T tree = this.trees[treeIndex];

        if (tree == null) {
            int treeOffsetX = this.baseOffsetX + (localX & ~0b111111);
            int treeOffsetY = this.baseOffsetY + (localY & ~0b111111);
            int treeOffsetZ = this.baseOffsetZ + (localZ & ~0b111111);
            tree = this.makeTree(treeOffsetX, treeOffsetY, treeOffsetZ);
            this.trees[treeIndex] = tree;
        }

        int result = addMethod.add(tree, x, y, z);
        this.lastTree = tree;
        return result == Tree.NOT_PRESENT;
    }

    @Override
    public int getPresence(int x, int y, int z) {
        if (this.lastTree != null) {
            int result = this.lastTree.getPresence(x, y, z);
            if (result != Tree.OUT_OF_BOUNDS) {
                return result;
            }
        }

        int localX = x - this.baseOffsetX;
        int localY = y - this.baseOffsetY;
        int localZ = z - this.baseOffsetZ;

        int treeIndex = this.getTreeIndex(localX, localY, localZ);
        if (treeIndex == Tree.OUT_OF_BOUNDS) {
            return Tree.OUT_OF_BOUNDS;
        }

        T tree = this.trees[treeIndex];
        if (tree != null) {
            this.lastTree = tree;
            return tree.getPresence(x, y, z);
        }
        return Tree.OUT_OF_BOUNDS;
    }

    protected abstract T[] makeTrees(int length);
}

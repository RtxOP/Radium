package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

public interface Forest<T extends Tree> {
    @FunctionalInterface
    interface TreeAddMethod<T extends Tree> {
        int add(T tree, int x, int y, int z);
    }

    default void add(int x, int y, int z) {
        this.add(x, y, z, Tree::add);
    }

    boolean add(int x, int y, int z, TreeAddMethod<T> addMethod);

    default void add(RenderSection section) {
        add(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    int getPresence(int x, int y, int z);

    default boolean isSectionPresent(int x, int y, int z) {
        return this.getPresence(x, y, z) == Tree.PRESENT;
    }
}

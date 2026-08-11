package net.caffeinemc.mods.sodium.client.render.chunk.lists;

public interface SortItemsProvider {
    int[] getCachedSortItems();

    void setCachedSortItems(int[] sortItems);

    default int[] ensureSortItemsOfLength(int length) {
        int[] sortItems = this.getCachedSortItems();
        if (sortItems == null || sortItems.length < length) {
            sortItems = new int[length];
            this.setCachedSortItems(sortItems);
        }
        return sortItems;
    }
}

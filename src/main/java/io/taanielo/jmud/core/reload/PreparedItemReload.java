package io.taanielo.jmud.core.reload;

import java.util.Optional;
import java.util.function.Function;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;

/**
 * A {@link PreparedReload} for items that also exposes the freshly loaded items by id.
 *
 * <p>The exposed lookup lets a room reload resolve item references against the new item snapshot
 * (before it is committed), keeping a combined item+room reload internally consistent (issue #349).
 */
public interface PreparedItemReload extends PreparedReload {

    /**
     * Finds a freshly prepared (not yet committed) item by id.
     *
     * @param id the item id to resolve
     * @return the matching prepared item, or empty when the snapshot has no such item
     */
    Optional<Item> find(ItemId id);

    /**
     * Creates a prepared item reload from an entry count, an item finder, and a commit action.
     *
     * @param count        the number of items read and validated
     * @param finder       resolves a freshly prepared item by id
     * @param commitAction the tick-thread action that swaps the backing item cache
     * @return a prepared item reload
     */
    static PreparedItemReload of(int count, Function<ItemId, Optional<Item>> finder, Runnable commitAction) {
        return new DefaultPreparedItemReload(count, finder, commitAction);
    }
}

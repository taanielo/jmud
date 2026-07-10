package io.taanielo.jmud.core.reload;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Default {@link PreparedItemReload} that resolves prepared items through a supplied finder and
 * delegates {@link #commit()} to a supplied action (issue #349).
 *
 * @param count        the number of items read and validated
 * @param finder       resolves a freshly prepared item by id
 * @param commitAction the tick-thread action that swaps the backing item cache
 */
record DefaultPreparedItemReload(int count, Function<ItemId, Optional<Item>> finder, Runnable commitAction)
    implements PreparedItemReload {

    DefaultPreparedItemReload {
        Objects.requireNonNull(finder, "Finder is required");
        Objects.requireNonNull(commitAction, "Commit action is required");
    }

    @Override
    public String contentType() {
        return "items";
    }

    @Override
    public Optional<Item> find(ItemId id) {
        return finder.apply(id);
    }

    @Override
    public void commit() {
        commitAction.run();
    }
}

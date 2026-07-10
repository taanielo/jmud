package io.taanielo.jmud.core.reload;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Orchestrates a transactional hot reload of game content (items, rooms, mob templates) from JSON
 * for the wizard {@code RELOAD} command (issue #349).
 *
 * <p>The reload is split into two phases to honour the single-writer tick loop (AGENTS.md §5):
 *
 * <ol>
 *   <li>{@link #prepare()} — runs off the tick thread. It reads and validates every backing file
 *       into an in-memory snapshot, resolving room item references against the freshly prepared
 *       items first (falling back to the live item repository). If any file fails to parse or
 *       validate it throws, having touched no live state.</li>
 *   <li>{@link PreparedContentReload#commit()} — runs on the tick thread. It atomically swaps every
 *       repository cache and reports the reloaded counts.</li>
 * </ol>
 *
 * <p>Because commit happens only after a fully successful prepare, a parse error never leaves the
 * world in a partially reloaded state.
 */
public final class ContentReloadService {

    private final ItemContentReloader itemReloader;
    private final RoomContentReloader roomReloader;
    private final @Nullable MobContentReloader mobReloader;
    private final ItemLookup liveItemLookup;

    /**
     * Creates the reload service.
     *
     * @param itemReloader   reads and validates item content off the tick thread
     * @param roomReloader   reads and validates room content off the tick thread
     * @param mobReloader    reads and validates mob-template content; {@code null} when the mob
     *                       subsystem failed to load
     * @param liveItemLookup resolves item references against the currently live item repository,
     *                       used as a fallback when a room references an unchanged item
     */
    public ContentReloadService(
        ItemContentReloader itemReloader,
        RoomContentReloader roomReloader,
        @Nullable MobContentReloader mobReloader,
        ItemLookup liveItemLookup
    ) {
        this.itemReloader = Objects.requireNonNull(itemReloader, "Item reloader is required");
        this.roomReloader = Objects.requireNonNull(roomReloader, "Room reloader is required");
        this.mobReloader = mobReloader;
        this.liveItemLookup = Objects.requireNonNull(liveItemLookup, "Live item lookup is required");
    }

    /**
     * Reads and validates all content off the tick thread, returning a committable snapshot.
     *
     * <p>This method performs blocking file I/O and must therefore never be called from the tick
     * thread (AGENTS.md §5); dispatch it to a background executor.
     *
     * @return the prepared reload, to be committed on the tick thread
     * @throws RepositoryException if any content file fails to parse or validate; no live state is
     *     mutated
     */
    public PreparedContentReload prepare() throws RepositoryException {
        PreparedItemReload preparedItems = itemReloader.prepareItems();
        ItemLookup combinedLookup = id -> {
            Optional<Item> fresh = preparedItems.find(id);
            return fresh.isPresent() ? fresh : liveItemLookup.find(id);
        };
        PreparedReload preparedRooms = roomReloader.prepareRooms(combinedLookup);
        PreparedReload preparedMobs = mobReloader == null ? null : mobReloader.prepareMobs();
        return new PreparedContentReload(preparedItems, preparedRooms, preparedMobs);
    }
}

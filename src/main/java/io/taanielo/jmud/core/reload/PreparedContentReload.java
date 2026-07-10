package io.taanielo.jmud.core.reload;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A validated, ready-to-commit reload of every content type, produced by
 * {@link ContentReloadService#prepare()} off the tick thread.
 *
 * <p>Holding the prepared per-type reloads together lets the wizard {@code RELOAD} command apply
 * them as a single unit: {@link #commit()} swaps every backing cache and returns the resulting
 * {@link ReloadReport}. It must be invoked on the tick thread (AGENTS.md §5).
 */
public final class PreparedContentReload {

    private final PreparedReload items;
    private final PreparedReload rooms;
    private final @Nullable PreparedReload mobs;

    PreparedContentReload(PreparedReload items, PreparedReload rooms, @Nullable PreparedReload mobs) {
        this.items = Objects.requireNonNull(items, "Prepared item reload is required");
        this.rooms = Objects.requireNonNull(rooms, "Prepared room reload is required");
        this.mobs = mobs;
    }

    /**
     * Applies every prepared snapshot to live game state and returns a summary of the counts.
     *
     * <p>Must be called on the tick thread. Items are committed before rooms so that already
     * resolved room state stays consistent with the item cache; each commit is an in-memory swap
     * that cannot fail (AGENTS.md §5).
     *
     * @return the report describing how many entries of each type were reloaded
     */
    public ReloadReport commit() {
        items.commit();
        rooms.commit();
        int mobCount = 0;
        if (mobs != null) {
            mobs.commit();
            mobCount = mobs.count();
        }
        return new ReloadReport(rooms.count(), items.count(), mobCount);
    }
}

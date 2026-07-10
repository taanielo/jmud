package io.taanielo.jmud.core.reload;

/**
 * A validated, in-memory reload of a single content type, ready to be applied to live game state.
 *
 * <p>A {@code PreparedReload} is produced off the tick thread by reading and validating every
 * backing JSON file (see the {@code *ContentReloader} interfaces). It holds no live game state
 * until {@link #commit()} is invoked, which must happen on the tick thread (AGENTS.md §5) so the
 * cache swap is observed atomically by the single writer.
 */
public interface PreparedReload {

    /**
     * Returns a short, human-readable label for the reloaded content type (e.g. {@code "rooms"}).
     *
     * @return the content type label
     */
    String contentType();

    /**
     * Returns the number of entries that were read and validated during prepare.
     *
     * @return the entry count; always &ge; 0
     */
    int count();

    /**
     * Applies the prepared snapshot to live game state by atomically swapping the backing cache.
     *
     * <p>Must be called on the tick thread. Implementations perform only an in-memory reference
     * swap and never any blocking I/O, so this is safe to run inside a tick (AGENTS.md §5).
     */
    void commit();

    /**
     * Creates a prepared reload from a content-type label, an entry count, and a commit action.
     *
     * <p>Repositories use this factory rather than a bespoke inner class so that the field swap
     * lives in the repository's own (lambda) method — keeping the arch rule that guards
     * {@code Json*Repository} access satisfied (AGENTS.md §3.3).
     *
     * @param contentType  the content type label (e.g. {@code "rooms"})
     * @param count        the number of entries read and validated
     * @param commitAction the tick-thread action that swaps the backing cache
     * @return a prepared reload delegating {@link #commit()} to {@code commitAction}
     */
    static PreparedReload of(String contentType, int count, Runnable commitAction) {
        return new DefaultPreparedReload(contentType, count, commitAction);
    }
}

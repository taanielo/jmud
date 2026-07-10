package io.taanielo.jmud.core.reload;

import java.util.Objects;

/**
 * Default {@link PreparedReload} that carries its content-type label and count as values and
 * delegates {@link #commit()} to a supplied action (issue #349).
 *
 * @param contentType  the content type label
 * @param count        the number of entries read and validated
 * @param commitAction the tick-thread action that swaps the backing cache
 */
record DefaultPreparedReload(String contentType, int count, Runnable commitAction) implements PreparedReload {

    DefaultPreparedReload {
        Objects.requireNonNull(contentType, "Content type is required");
        Objects.requireNonNull(commitAction, "Commit action is required");
    }

    @Override
    public void commit() {
        commitAction.run();
    }
}

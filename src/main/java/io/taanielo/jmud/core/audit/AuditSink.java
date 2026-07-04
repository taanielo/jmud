package io.taanielo.jmud.core.audit;

import java.time.Duration;

public interface AuditSink {
    void write(AuditEntry entry);

    /**
     * Blocks (up to {@code timeout}) until any buffered entries have been handed
     * off to the underlying storage. Sinks with no internal buffering can rely on
     * the default no-op.
     *
     * @param timeout the maximum time to wait for buffered entries to drain
     * @return true if the sink was fully drained before the timeout elapsed
     */
    default boolean flush(Duration timeout) {
        return true;
    }

    /**
     * Releases any resources (file handles, worker threads) held by this sink.
     * Safe to call multiple times.
     */
    default void close() {
    }
}

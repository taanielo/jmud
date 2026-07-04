package io.taanielo.jmud.core.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link AsyncAuditSink#flush(Duration)} drains queued entries to the
 * delegate sink, and that {@link AsyncAuditSink#close()} stops the worker and
 * closes the delegate (issue #170).
 */
class AsyncAuditSinkTest {

    @Test
    void flushDrainsQueuedEntriesBeforeTimeoutElapses() {
        RecordingSink delegate = new RecordingSink();
        AsyncAuditSink sink = new AsyncAuditSink(delegate, 100);

        for (int i = 0; i < 20; i++) {
            sink.write(entry(i));
        }

        boolean drained = sink.flush(Duration.ofSeconds(2));

        assertTrue(drained, "Queue should be fully drained within the timeout");
        assertEquals(20, delegate.entries.size(), "All written entries must reach the delegate sink");

        sink.close();
        assertTrue(delegate.closed.get(), "close() must close the delegate sink");
    }

    @Test
    void flushReturnsFalseWhenDelegateIsTooSlow() {
        SlowSink delegate = new SlowSink(Duration.ofMillis(500));
        AsyncAuditSink sink = new AsyncAuditSink(delegate, 100);

        sink.write(entry(1));
        sink.write(entry(2));
        sink.write(entry(3));

        boolean drained = sink.flush(Duration.ofMillis(50));

        assertTrue(!drained, "Flush must report timeout when the delegate can't keep up");

        sink.close();
    }

    private static AuditEntry entry(int i) {
        return new AuditEntry(1, System.currentTimeMillis(), "test.event", "corr-" + i, 0L, null, null, null, "success", null);
    }

    private static final class RecordingSink implements AuditSink {
        private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void write(AuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class SlowSink implements AuditSink {
        private final Duration delayPerEntry;

        private SlowSink(Duration delayPerEntry) {
            this.delayPerEntry = delayPerEntry;
        }

        @Override
        public void write(AuditEntry entry) {
            try {
                Thread.sleep(delayPerEntry.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

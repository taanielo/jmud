package io.taanielo.jmud.core.audit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncAuditSink implements AuditSink {
    private final AuditSink delegate;
    private final BlockingQueue<AuditEntry> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    public AsyncAuditSink(AuditSink delegate, int capacity) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate sink is required");
        if (capacity < 1) {
            throw new IllegalArgumentException("Queue capacity must be >= 1");
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.worker = new Thread(this::drainQueue, "audit-writer");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public void write(AuditEntry entry) {
        if (entry == null) {
            return;
        }
        boolean accepted = queue.offer(entry);
        if (!accepted) {
            log.warn("Audit queue full, dropping entry");
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        worker.interrupt();
    }

    /**
     * Blocks (up to {@code timeout}) while the background worker drains the
     * queue, without stopping the worker. Call {@link #close()} afterward to
     * stop the worker thread and release the delegate sink.
     *
     * @param timeout the maximum time to wait for the queue to drain
     * @return true if the queue was empty before the timeout elapsed
     */
    @Override
    public boolean flush(Duration timeout) {
        Objects.requireNonNull(timeout, "Timeout is required");
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (!queue.isEmpty()) {
            if (System.nanoTime() >= deadlineNanos) {
                log.warn("Audit queue flush timed out with {} entries remaining", queue.size());
                return false;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return queue.isEmpty();
            }
        }
        return true;
    }

    /**
     * Stops the background worker and closes the delegate sink. Safe to call
     * multiple times.
     */
    @Override
    public void close() {
        stop();
        try {
            worker.join(Duration.ofSeconds(2).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        delegate.close();
    }

    private void drainQueue() {
        while (running.get() || !queue.isEmpty()) {
            try {
                AuditEntry entry = queue.poll(250, TimeUnit.MILLISECONDS);
                if (entry == null) {
                    continue;
                }
                delegate.write(entry);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("Failed to flush audit entry", e);
            }
        }
    }
}

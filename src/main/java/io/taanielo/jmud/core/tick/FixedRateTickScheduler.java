package io.taanielo.jmud.core.tick;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * Single-writer tick loop scheduler. Drains every registered {@link Tickable} at a fixed
 * interval on one dedicated thread; player command queues, mob AI, effects and other game
 * state mutations run here and nowhere else. Also tracks per-tick duration and overrun
 * counters so tick health can be observed without profiling.
 */
@Slf4j
public class FixedRateTickScheduler {

    private final TickRegistry tickRegistry;
    private final ScheduledExecutorService executor;
    private final long intervalMillis;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicLong tickCounter = new AtomicLong();
    private final AtomicLong overrunCount = new AtomicLong();
    private volatile long lastTickNanos;
    private volatile long maxTickNanos;
    private volatile boolean previousTickOverran;
    private ScheduledFuture<?> task;

    public FixedRateTickScheduler(TickRegistry tickRegistry) {
        this(
            tickRegistry,
            TickSettings.intervalMillis(),
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("tick-", 0).factory())
        );
    }

    public FixedRateTickScheduler(TickRegistry tickRegistry, long intervalMillis, ScheduledExecutorService executor) {
        this.tickRegistry = Objects.requireNonNull(tickRegistry, "Tick registry is required");
        this.executor = Objects.requireNonNull(executor, "Executor is required");
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("Tick interval must be positive");
        }
        this.intervalMillis = intervalMillis;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        task = executor.scheduleAtFixedRate(this::runTick, 0, intervalMillis, TimeUnit.MILLISECONDS);
        log.info("Tick scheduler started with interval {} ms", intervalMillis);
    }

    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        if (task != null) {
            task.cancel(false);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        log.info("Tick scheduler stopped");
    }

    /**
     * Package-private test hook that runs a single tick synchronously on the calling thread,
     * bypassing the scheduler. Production code always goes through {@link #start()}.
     */
    void runTickForTest() {
        runTick();
    }

    private void runTick() {
        long tick = tickCounter.incrementAndGet();
        java.util.List<Tickable> snapshot = tickRegistry.snapshot();
        log.debug("Tick {} start ({} tickables)", tick, snapshot.size());
        boolean traceSlowest = previousTickOverran;
        Tickable slowest = null;
        long slowestNanos = -1;

        long startNanos = System.nanoTime();
        for (Tickable tickable : snapshot) {
            long tickableStart = traceSlowest ? System.nanoTime() : 0L;
            try {
                tickable.tick();
            } catch (Exception e) {
                log.error("Tickable failed during tick", e);
            }
            if (traceSlowest) {
                long tickableElapsed = System.nanoTime() - tickableStart;
                if (tickableElapsed > slowestNanos) {
                    slowestNanos = tickableElapsed;
                    slowest = tickable;
                }
            }
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        lastTickNanos = elapsedNanos;
        if (elapsedNanos > maxTickNanos) {
            maxTickNanos = elapsedNanos;
        }

        long elapsedMs = elapsedNanos / 1_000_000L;
        boolean overran = elapsedMs > intervalMillis;
        if (overran) {
            overrunCount.incrementAndGet();
            log.warn(
                "Tick {} overran: {} ms (budget {} ms, {} tickables)",
                tick,
                elapsedMs,
                intervalMillis,
                snapshot.size()
            );
        }
        previousTickOverran = overran;

        if (traceSlowest && slowest != null) {
            log.debug(
                "Tick {} slowest tickable: {} ({} ms)",
                tick,
                slowest.getClass().getName(),
                slowestNanos / 1_000_000L
            );
        }
    }

    /**
     * Number of ticks that have exceeded the configured interval budget since scheduler creation.
     *
     * @return the running overrun count
     */
    public long overrunCount() {
        return overrunCount.get();
    }

    /**
     * Duration of the most recently completed tick.
     *
     * @return elapsed nanoseconds of the last tick
     */
    public long lastTickDurationNanos() {
        return lastTickNanos;
    }

    /**
     * Longest observed tick duration since scheduler creation.
     *
     * @return elapsed nanoseconds of the slowest tick observed so far
     */
    public long maxTickDurationNanos() {
        return maxTickNanos;
    }
}

package io.taanielo.jmud.core.tick;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * Single-writer tick loop scheduler. Drains every registered {@link Tickable} at a fixed
 * interval on one dedicated thread; player command queues, mob AI, effects and other game
 * state mutations run here and nowhere else. Also tracks per-tick duration and overrun
 * counters so tick health can be observed without profiling.
 *
 * <p>When a {@link MeterRegistry} is provided (via the four-argument constructor or the
 * two-argument convenience constructor that accepts a registry), two additional meters are
 * registered:
 * <ul>
 *   <li>{@code jmud.tick.duration} — a {@link Timer} recording the wall-clock duration of
 *       every call to {@link #runTick()}, giving percentile and histogram data in JMX.</li>
 *   <li>{@code jmud.tick.overruns} — a {@link Counter} incremented each time a tick
 *       exceeds the configured interval budget.</li>
 * </ul>
 */
@Slf4j
public class FixedRateTickScheduler {

    private final TickRegistry tickRegistry;
    private final ScheduledExecutorService executor;
    private final long intervalMillis;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicLong overrunCount = new AtomicLong();
    private final Timer tickTimer;
    private final Counter overrunMicrometerCounter;
    private volatile long lastTickNanos;
    private volatile long maxTickNanos;
    private volatile boolean previousTickOverran;
    private @Nullable ScheduledFuture<?> task;

    /**
     * Creates a scheduler using the default tick interval from {@link TickSettings}.
     * Metrics are disabled (no-op registry).
     *
     * @param tickRegistry the registry of tickables to drain on each tick
     */
    public FixedRateTickScheduler(TickRegistry tickRegistry) {
        this(tickRegistry, new CompositeMeterRegistry());
    }

    /**
     * Creates a scheduler using the default tick interval from {@link TickSettings}
     * and the supplied meter registry for tick-health metrics.
     *
     * @param tickRegistry  the registry of tickables to drain on each tick
     * @param meterRegistry the Micrometer registry to record tick-health meters into;
     *                      must not be null (pass an empty {@link CompositeMeterRegistry}
     *                      for no-op behaviour)
     */
    public FixedRateTickScheduler(TickRegistry tickRegistry, MeterRegistry meterRegistry) {
        this(
            tickRegistry,
            TickSettings.intervalMillis(),
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("tick-", 0).factory()),
            meterRegistry
        );
    }

    /**
     * Creates a scheduler with explicit interval and executor. Metrics are disabled
     * (no-op registry). Primarily used in tests that need fine-grained control.
     *
     * @param tickRegistry  the registry of tickables to drain on each tick
     * @param intervalMillis tick budget in milliseconds; must be positive
     * @param executor      the scheduled executor that fires the tick at the given interval
     */
    public FixedRateTickScheduler(TickRegistry tickRegistry, long intervalMillis, ScheduledExecutorService executor) {
        this(tickRegistry, intervalMillis, executor, new CompositeMeterRegistry());
    }

    /**
     * Full constructor used by both the convenience constructors and tests that need
     * a custom {@link MeterRegistry}.
     *
     * @param tickRegistry  the registry of tickables to drain on each tick
     * @param intervalMillis tick budget in milliseconds; must be positive
     * @param executor      the scheduled executor that fires the tick at the given interval
     * @param meterRegistry the Micrometer registry to record tick-health meters into
     */
    public FixedRateTickScheduler(
        TickRegistry tickRegistry,
        long intervalMillis,
        ScheduledExecutorService executor,
        MeterRegistry meterRegistry
    ) {
        this.tickRegistry = Objects.requireNonNull(tickRegistry, "Tick registry is required");
        this.executor = Objects.requireNonNull(executor, "Executor is required");
        Objects.requireNonNull(meterRegistry, "Meter registry is required");
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("Tick interval must be positive");
        }
        this.intervalMillis = intervalMillis;
        this.tickTimer = Timer.builder("jmud.tick.duration")
            .description("Wall-clock duration of each game tick")
            .publishPercentileHistogram()
            .register(meterRegistry);
        this.overrunMicrometerCounter = Counter.builder("jmud.tick.overruns")
            .description("Number of ticks that exceeded the configured interval budget")
            .register(meterRegistry);
    }

    /** Starts the fixed-rate tick loop. Calling this more than once is a no-op. */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        task = executor.scheduleAtFixedRate(this::runTick, 0, intervalMillis, TimeUnit.MILLISECONDS);
        log.info("Tick scheduler started with interval {} ms", intervalMillis);
    }

    /** Stops the fixed-rate tick loop. Calling this more than once is a no-op. */
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
     * Test hook that runs a single tick synchronously on the calling thread,
     * bypassing the scheduler. Production code always goes through {@link #start()}.
     */
    public void runTickForTest() {
        runTick();
    }

    private void runTick() {
        List<Tickable> snapshot = tickRegistry.snapshot();
        log.debug("Tick start ({} tickables)", snapshot.size());
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

        tickTimer.record(elapsedNanos, TimeUnit.NANOSECONDS);

        long elapsedMs = elapsedNanos / 1_000_000L;
        boolean overran = elapsedMs > intervalMillis;
        if (overran) {
            overrunCount.incrementAndGet();
            overrunMicrometerCounter.increment();
            log.warn(
                "Tick overran: {} ms (budget {} ms, {} tickables)",
                elapsedMs,
                intervalMillis,
                snapshot.size()
            );
        }
        previousTickOverran = overran;

        if (traceSlowest && slowest != null) {
            log.debug(
                "Tick slowest tickable: {} ({} ms)",
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

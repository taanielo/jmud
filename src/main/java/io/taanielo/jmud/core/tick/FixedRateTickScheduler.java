package io.taanielo.jmud.core.tick;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FixedRateTickScheduler implements TickScheduler {

    private final TickRegistry tickRegistry;
    private final ScheduledExecutorService executor;
    private final ExecutorService tickExecutor;
    private final long intervalMillis;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ScheduledFuture<?> task;

    public FixedRateTickScheduler(TickRegistry tickRegistry) {
        this(
            tickRegistry,
            TickSettings.resolveIntervalMillis(),
            Executors.newScheduledThreadPool(1, Thread.ofVirtual().name("tick-", 0).factory()),
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("tick-worker-", 0).factory())
        );
    }

    public FixedRateTickScheduler(
        TickRegistry tickRegistry,
        long intervalMillis,
        ScheduledExecutorService executor,
        ExecutorService tickExecutor
    ) {
        this.tickRegistry = Objects.requireNonNull(tickRegistry, "Tick registry is required");
        this.executor = Objects.requireNonNull(executor, "Executor is required");
        this.tickExecutor = Objects.requireNonNull(tickExecutor, "Tick executor is required");
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("Tick interval must be positive");
        }
        this.intervalMillis = intervalMillis;
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        task = executor.scheduleAtFixedRate(this::runTick, 0, intervalMillis, TimeUnit.MILLISECONDS);
        log.info("Tick scheduler started with interval {} ms", intervalMillis);
    }

    @Override
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        if (task != null) {
            task.cancel(false);
        }
        executor.shutdown();
        tickExecutor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!tickExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                tickExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            tickExecutor.shutdownNow();
        }
        log.info("Tick scheduler stopped");
    }

    private void runTick() {
        for (Tickable tickable : tickRegistry.snapshot()) {
            tickExecutor.execute(() -> runTickableSafely(tickable));
        }
    }

    private void runTickableSafely(Tickable tickable) {
        try {
            tickable.tick();
        } catch (Exception e) {
            log.error("Tickable failed during tick", e);
        }
    }
}

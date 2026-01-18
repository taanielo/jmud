package io.taanielo.jmud.core.tick;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FixedRateTickScheduler {

    private final TickRegistry tickRegistry;
    private final ScheduledExecutorService executor;
    private final long intervalMillis;
    private final AtomicBoolean started = new AtomicBoolean(false);
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

    private void runTick() {
        for (Tickable tickable : tickRegistry.snapshot()) {
            try {
                tickable.tick();
            } catch (Exception e) {
                log.error("Tickable failed during tick", e);
            }
        }
    }
}

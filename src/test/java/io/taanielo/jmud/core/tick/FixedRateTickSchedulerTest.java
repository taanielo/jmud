package io.taanielo.jmud.core.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class FixedRateTickSchedulerTest {

    @Test
    void invokesTickablesOnSchedule() throws Exception {
        TickRegistry registry = new TickRegistry();
        AtomicInteger ticks = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(3);
        registry.register(() -> {
            ticks.incrementAndGet();
            latch.countDown();
        });

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("tick-test-", 0).factory()
        );
        FixedRateTickScheduler scheduler = new FixedRateTickScheduler(
            registry,
            10,
            executor
        );

        scheduler.start();
        boolean completed = latch.await(1, TimeUnit.SECONDS);
        scheduler.stop();

        assertTrue(completed);
        assertTrue(ticks.get() >= 3);
    }

    @Test
    void recordsOverrunWhenTickExceedsBudget() {
        TickRegistry registry = new TickRegistry();
        long intervalMillis = 20;
        registry.register(() -> {
            try {
                Thread.sleep(intervalMillis + 50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        FixedRateTickScheduler scheduler = new FixedRateTickScheduler(
            registry,
            intervalMillis,
            new DirectScheduledExecutorService()
        );

        scheduler.runTickForTest();

        assertEquals(1, scheduler.overrunCount());
        assertTrue(scheduler.lastTickDurationNanos() >= TimeUnit.MILLISECONDS.toNanos(intervalMillis + 50));
        assertTrue(scheduler.maxTickDurationNanos() >= scheduler.lastTickDurationNanos());
    }

    @Test
    void doesNotRecordOverrunForFastTicks() {
        TickRegistry registry = new TickRegistry();
        registry.register(() -> { });

        FixedRateTickScheduler scheduler = new FixedRateTickScheduler(
            registry,
            1000,
            new DirectScheduledExecutorService()
        );

        scheduler.runTickForTest();

        assertEquals(0, scheduler.overrunCount());
    }

    /**
     * Minimal synchronous {@link ScheduledExecutorService} that runs submitted tasks
     * immediately on the calling thread, giving deterministic tests without real scheduling.
     */
    private static final class DirectScheduledExecutorService extends AbstractExecutorService
        implements ScheduledExecutorService {

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            return java.util.List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}

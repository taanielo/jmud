package io.taanielo.jmud.core.tick;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
            executor,
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("tick-worker-test-", 0).factory())
        );

        scheduler.start();
        boolean completed = latch.await(1, TimeUnit.SECONDS);
        scheduler.stop();

        assertTrue(completed);
        assertTrue(ticks.get() >= 3);
    }
}

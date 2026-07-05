package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PlayerCommandQueueTest {

    @Test
    void executesCommandsInOrderAndContinuesAfterFailure() {
        PlayerCommandQueue queue = new PlayerCommandQueue();
        List<String> executed = new ArrayList<>();

        queue.enqueue(() -> executed.add("first"));
        queue.enqueue(() -> {
            executed.add("boom");
            throw new IllegalStateException("fail");
        });
        queue.enqueue(() -> executed.add("third"));

        queue.tick();

        assertEquals(List.of("first", "boom", "third"), executed);
    }

    @Test
    void rejectsCommandsBeyondCapacityWithoutBlocking() {
        PlayerCommandQueue queue = new PlayerCommandQueue(2, 5);

        assertTrue(queue.enqueue(() -> { }));
        assertTrue(queue.enqueue(() -> { }));
        assertFalse(queue.enqueue(() -> { }), "Offer past capacity must be rejected");
        assertEquals(2, queue.size(), "Dropped command must not be queued");
    }

    @Test
    void acceptsAgainAfterDraining() {
        PlayerCommandQueue queue = new PlayerCommandQueue(1, 5);

        assertTrue(queue.enqueue(() -> { }));
        assertFalse(queue.enqueue(() -> { }));

        queue.tick();

        assertTrue(queue.enqueue(() -> { }), "Capacity must be released after drain");
    }

    @Test
    void drainsAtMostMaxCommandsPerTickAndPreservesOrderAcrossTicks() {
        PlayerCommandQueue queue = new PlayerCommandQueue(10, 3);
        List<Integer> executed = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            int value = i;
            assertTrue(queue.enqueue(() -> executed.add(value)));
        }

        queue.tick();
        assertEquals(List.of(0, 1, 2), executed, "First tick drains at most the cap, in order");
        assertEquals(4, queue.size(), "Remainder carries over to the next tick");

        queue.tick();
        assertEquals(List.of(0, 1, 2, 3, 4, 5), executed);

        queue.tick();
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), executed);
        assertEquals(0, queue.size());
    }

    @Test
    void concurrentOffersNeverExceedCapacity() throws InterruptedException {
        int capacity = 20;
        PlayerCommandQueue queue = new PlayerCommandQueue(capacity, 5);
        int threads = 4;
        int offersPerThread = 50;
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    for (int j = 0; j < offersPerThread; j++) {
                        if (queue.enqueue(() -> { })) {
                            accepted.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Offer threads must finish");

        assertEquals(capacity, accepted.get(), "Exactly the capacity worth of offers is accepted");
        assertEquals(capacity, queue.size());
    }

    @Test
    void rejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerCommandQueue(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new PlayerCommandQueue(10, 0));
    }
}

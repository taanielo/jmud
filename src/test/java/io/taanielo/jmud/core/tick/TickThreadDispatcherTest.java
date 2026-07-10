package io.taanielo.jmud.core.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TickThreadDispatcherTest {

    private static void tickAll(TickRegistry registry) {
        registry.snapshot().forEach(Tickable::tick);
    }

    @Test
    void runsTaskOnNextTick() {
        TickRegistry registry = new TickRegistry();
        TickThreadDispatcher dispatcher = new TickThreadDispatcher(registry);
        AtomicInteger runs = new AtomicInteger();

        dispatcher.runOnNextTick(runs::incrementAndGet);
        assertEquals(0, runs.get(), "task must not run before the next tick");

        tickAll(registry);
        assertEquals(1, runs.get());
    }

    @Test
    void runsTaskExactlyOnceAndUnsubscribes() {
        TickRegistry registry = new TickRegistry();
        TickThreadDispatcher dispatcher = new TickThreadDispatcher(registry);
        AtomicInteger runs = new AtomicInteger();

        dispatcher.runOnNextTick(runs::incrementAndGet);
        tickAll(registry);
        tickAll(registry);
        tickAll(registry);

        assertEquals(1, runs.get(), "one-shot task must run only once");
        assertTrue(registry.snapshot().isEmpty(), "dispatcher must unsubscribe its tickable");
    }
}

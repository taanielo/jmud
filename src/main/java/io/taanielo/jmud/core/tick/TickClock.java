package io.taanielo.jmud.core.tick;

import java.util.concurrent.atomic.AtomicLong;

public class TickClock implements Tickable {
    private final AtomicLong tick = new AtomicLong();

    @Override
    public void tick() {
        tick.incrementAndGet();
    }

    public long currentTick() {
        return tick.get();
    }
}

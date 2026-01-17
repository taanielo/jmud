package io.taanielo.jmud.core.tick;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TickRegistry {
    private final CopyOnWriteArrayList<Tickable> tickables = new CopyOnWriteArrayList<>();

    public void register(Tickable tickable) {
        if (tickable == null) {
            throw new IllegalArgumentException("Tickable is required");
        }
        tickables.addIfAbsent(tickable);
    }

    public void unregister(Tickable tickable) {
        tickables.remove(tickable);
    }

    public List<Tickable> snapshot() {
        return List.copyOf(tickables);
    }
}

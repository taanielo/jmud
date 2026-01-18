package io.taanielo.jmud.core.tick;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TickRegistry {
    private final CopyOnWriteArrayList<Tickable> tickables = new CopyOnWriteArrayList<>();

    /**
     * Register a Tickable and return a subscription that should be stored and
     * used to unsubscribe when the Tickable lifecycle ends.
     */
    public TickSubscription register(Tickable tickable) {
        if (tickable == null) {
            throw new IllegalArgumentException("Tickable is required");
        }
        tickables.addIfAbsent(tickable);
        return () -> tickables.remove(tickable);
    }

    public void unregister(Tickable tickable) {
        tickables.remove(tickable);
    }

    public void clear() {
        tickables.clear();
    }

    public List<Tickable> snapshot() {
        return List.copyOf(tickables);
    }
}

package io.taanielo.jmud.core.tick;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Runs a one-off task on the tick thread by registering a self-unsubscribing {@link Tickable}.
 *
 * <p>Some flows (e.g. the wizard {@code RELOAD} command, issue #349) do their heavy work off the
 * tick thread and then need to apply the result to game state, which must happen on the single
 * writer thread (AGENTS.md §5). This dispatcher bridges that gap without exposing the raw
 * {@link TickRegistry} to callers: the submitted task runs exactly once, on the next tick after it
 * is submitted, and the backing {@link Tickable} unsubscribes itself afterwards.
 */
public final class TickThreadDispatcher {

    private final TickRegistry tickRegistry;

    /**
     * Creates a dispatcher backed by the given tick registry.
     *
     * @param tickRegistry the registry whose tick thread will run submitted tasks
     */
    public TickThreadDispatcher(TickRegistry tickRegistry) {
        this.tickRegistry = Objects.requireNonNull(tickRegistry, "Tick registry is required");
    }

    /**
     * Schedules the given task to run once on the next tick.
     *
     * <p>Safe to call from any thread. The task runs on the tick thread, so it may freely read and
     * mutate game state; it should not perform blocking I/O (AGENTS.md §5).
     *
     * @param task the task to run on the tick thread
     */
    public void runOnNextTick(Runnable task) {
        Objects.requireNonNull(task, "Task is required");
        OneShotTickable tickable = new OneShotTickable(task);
        tickable.subscription = tickRegistry.register(tickable);
    }

    /**
     * A {@link Tickable} that runs its task at most once and then removes itself from the registry.
     * The subscription may still be {@code null} on the very first tick if a tick races the
     * registration, so unsubscription is retried on each subsequent tick until it succeeds.
     */
    private static final class OneShotTickable implements Tickable {

        private final Runnable task;
        private volatile boolean ran;
        private volatile @Nullable TickSubscription subscription;

        private OneShotTickable(Runnable task) {
            this.task = task;
        }

        @Override
        public void tick() {
            if (!ran) {
                ran = true;
                task.run();
            }
            TickSubscription current = subscription;
            if (current != null) {
                current.unsubscribe();
            }
        }
    }
}

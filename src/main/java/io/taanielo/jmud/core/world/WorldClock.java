package io.taanielo.jmud.core.world;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.taanielo.jmud.core.tick.Tickable;

/**
 * A deterministic, tick-count-driven world clock that flips between {@link TimeOfDay#DAY} and
 * {@link TimeOfDay#NIGHT} every {@code ticksPerPhase} ticks.
 *
 * <p>Follows the same pattern as {@link io.taanielo.jmud.core.tick.TickClock}: an atomic counter
 * incremented in {@link #tick()} (called once per tick, on the tick thread only, via
 * {@link io.taanielo.jmud.core.tick.TickRegistry}) with pure query methods that are safe to read
 * as a snapshot from any thread. Never reads the wall clock, so it stays deterministic across
 * runs and tick rates, per AGENTS.md §5.
 */
public class WorldClock implements Tickable {

    private final int ticksPerPhase;
    private final AtomicLong ticksSinceTransition = new AtomicLong();
    private final AtomicReference<TimeOfDay> timeOfDay = new AtomicReference<>(TimeOfDay.DAY);

    /**
     * Creates a world clock that transitions every {@code ticksPerPhase} ticks, starting at
     * {@link TimeOfDay#DAY}.
     *
     * @param ticksPerPhase the number of ticks each phase (day or night) lasts; must be positive
     */
    public WorldClock(int ticksPerPhase) {
        if (ticksPerPhase <= 0) {
            throw new IllegalArgumentException("ticksPerPhase must be positive");
        }
        this.ticksPerPhase = ticksPerPhase;
    }

    /**
     * Advances the clock by one tick, flipping {@link #timeOfDay()} when the current phase has
     * lasted {@code ticksPerPhase} ticks. Must only be called from the tick thread.
     */
    @Override
    public void tick() {
        long elapsed = ticksSinceTransition.incrementAndGet();
        if (elapsed >= ticksPerPhase) {
            timeOfDay.updateAndGet(TimeOfDay::opposite);
            ticksSinceTransition.set(0);
        }
    }

    /**
     * Returns the current phase of the day/night cycle. Safe to call from any thread as a
     * point-in-time snapshot.
     */
    public TimeOfDay timeOfDay() {
        return timeOfDay.get();
    }

    /**
     * Returns the number of ticks elapsed since the last day/night transition.
     */
    public long ticksSinceTransition() {
        return ticksSinceTransition.get();
    }

    /**
     * Returns the configured number of ticks per phase.
     */
    public int ticksPerPhase() {
        return ticksPerPhase;
    }
}

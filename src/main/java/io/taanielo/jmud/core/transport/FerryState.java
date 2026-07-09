package io.taanielo.jmud.core.transport;

/**
 * Immutable runtime snapshot of a single ferry's schedule position, owned by {@link BoatEngine} and
 * confined to the tick thread (AGENTS.md §5).
 *
 * <p>{@link #currentLegIndex()} is the index into the ferry's route of the dock it is presently
 * moored at; {@link #countdown()} is the number of ticks remaining before it departs for the next
 * dock. A fresh instance is produced each tick rather than mutating in place, so there is no shared
 * mutable state to synchronize.
 *
 * @param currentLegIndex the index into {@link Ferry#route()} of the current dock
 * @param countdown       ticks remaining until the next departure
 */
public record FerryState(int currentLegIndex, int countdown) {

    /**
     * Creates the initial state for a ferry: moored at its configured starting dock with a full
     * leg's worth of ticks before its first departure.
     *
     * @param ferry the ferry to seed state for
     * @return the initial schedule state
     */
    public static FerryState initial(Ferry ferry) {
        return new FerryState(ferry.startLegIndex(), ferry.ticksPerLeg());
    }
}

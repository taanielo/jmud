package io.taanielo.jmud.core.world;

/**
 * The two phases of the deterministic, tick-driven day/night cycle tracked by {@link WorldClock}.
 */
public enum TimeOfDay {
    DAY,
    NIGHT;

    /**
     * Returns the other phase of the cycle (the phase this one transitions into).
     */
    public TimeOfDay opposite() {
        return this == DAY ? NIGHT : DAY;
    }
}

package io.taanielo.jmud.core.ability;

public enum AbilityTargeting {
    /** Targets a hostile entity; usable at any time during or outside combat. */
    HARMFUL,
    /** Targets a friendly entity (defaults to self when no target is provided). */
    BENEFICIAL,
    /**
     * Targets a hostile entity but may only be used as an opening strike —
     * i.e. the source must not already be engaged in combat with any target.
     */
    HARMFUL_OPENER
}

package io.taanielo.jmud.core.player;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration accessor for the hunger/thirst (sustenance) mechanic.
 *
 * <p>All values are deterministic and tick-based (AGENTS.md §5); no wall-clock timers are
 * involved. Decay is applied once per tick by {@link SustenanceTicker}.
 */
public final class SustenanceSettings {

    /** Whether hunger/thirst decay is enabled by default. */
    public static final boolean DEFAULT_ENABLED = true;

    /** Default number of hunger/thirst points lost per tick. */
    public static final int DEFAULT_DECAY_PER_TICK = 1;

    private static final GameConfig CONFIG = GameConfig.load();

    private SustenanceSettings() {
    }

    /**
     * Returns {@code true} when hunger/thirst decay is enabled.
     *
     * @return whether the sustenance mechanic is active
     */
    public static boolean enabled() {
        return CONFIG.getBoolean("jmud.sustenance.enabled", DEFAULT_ENABLED);
    }

    /**
     * Returns the number of hunger/thirst points lost per tick.
     *
     * @return the per-tick decay amount; never negative
     */
    public static int decayPerTick() {
        int decay = CONFIG.getInt("jmud.sustenance.decay_per_tick", DEFAULT_DECAY_PER_TICK);
        if (decay < 0) {
            throw new IllegalArgumentException("Sustenance decay per tick must be non-negative");
        }
        return decay;
    }
}

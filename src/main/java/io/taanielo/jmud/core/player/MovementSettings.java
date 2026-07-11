package io.taanielo.jmud.core.player;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration for the movement move-point cost system.
 *
 * <p>Values are read from {@code jmud.properties} on first access and may be overridden with JVM
 * system properties (for example {@code -Djmud.movement.step_cost=2}).
 */
public final class MovementSettings {

    /** Default move points a single room step costs. */
    public static final int DEFAULT_STEP_COST = 1;
    /** Default extra move points charged per step while overburdened. */
    public static final int DEFAULT_OVERBURDENED_SURCHARGE = 1;

    private static final GameConfig CONFIG = GameConfig.load();

    private MovementSettings() {
    }

    /**
     * Returns the baseline move-point cost of walking one room. Defaults to
     * {@value #DEFAULT_STEP_COST}, matching the rest-regen move granularity so a step spends what a
     * single rest tick restores.
     */
    public static int stepCost() {
        int cost = CONFIG.getInt("jmud.movement.step_cost", DEFAULT_STEP_COST);
        if (cost < 0) {
            throw new IllegalArgumentException("Move step cost must be non-negative");
        }
        return cost;
    }

    /**
     * Returns the additional move-point cost charged per step while a player is overburdened.
     * Defaults to {@value #DEFAULT_OVERBURDENED_SURCHARGE}.
     */
    public static int overburdenedSurcharge() {
        int surcharge = CONFIG.getInt("jmud.movement.overburdened_surcharge", DEFAULT_OVERBURDENED_SURCHARGE);
        if (surcharge < 0) {
            throw new IllegalArgumentException("Overburdened move surcharge must be non-negative");
        }
        return surcharge;
    }
}

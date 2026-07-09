package io.taanielo.jmud.core.weather;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration for {@link WeatherEngine}: how often the weather picks a new target and how fast
 * intensity ramps between states. Follows the same config-key-with-default pattern as
 * {@code WorldClockSettings}.
 */
public final class WeatherSettings {

    /** Default number of ticks between weather target re-rolls. */
    public static final int DEFAULT_TRANSITION_INTERVAL_TICKS = 20;

    /** Default intensity change (0-100 scale) applied per tick for smooth transitions. */
    public static final int DEFAULT_INTENSITY_STEP = 10;

    private static final GameConfig CONFIG = GameConfig.load();

    private WeatherSettings() {
    }

    /**
     * Returns the number of ticks between weather target re-rolls, read from
     * {@code jmud.weather.transition_interval_ticks} (defaulting to
     * {@link #DEFAULT_TRANSITION_INTERVAL_TICKS}).
     */
    public static int transitionIntervalTicks() {
        int ticks = CONFIG.getInt(
            "jmud.weather.transition_interval_ticks", DEFAULT_TRANSITION_INTERVAL_TICKS);
        if (ticks <= 0) {
            throw new IllegalArgumentException("Weather transition interval must be positive");
        }
        return ticks;
    }

    /**
     * Returns the per-tick intensity step, read from {@code jmud.weather.intensity_step}
     * (defaulting to {@link #DEFAULT_INTENSITY_STEP}).
     */
    public static int intensityStep() {
        int step = CONFIG.getInt("jmud.weather.intensity_step", DEFAULT_INTENSITY_STEP);
        if (step <= 0) {
            throw new IllegalArgumentException("Weather intensity step must be positive");
        }
        return step;
    }
}

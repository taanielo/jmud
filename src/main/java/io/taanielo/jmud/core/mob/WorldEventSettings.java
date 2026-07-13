package io.taanielo.jmud.core.mob;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * World-event tunables read from {@code jmud.properties} (overridable via JVM system properties),
 * mirroring the {@code jmud.combat.mob_flee_*} accessor pattern in
 * {@link io.taanielo.jmud.core.combat.CombatSettings}.
 *
 * <p>A world event opens on a randomized interval drawn from
 * {@code [minIntervalTicks, maxIntervalTicks]} and, once open, stays killable for
 * {@code windowTicks} ticks before its rare-elite mob fades away unkilled. Every value is expressed
 * in ticks so the schedule is deterministic under a seeded world (AGENTS.md §5).
 */
public final class WorldEventSettings {

    /** Default lower bound (in ticks) of the gap between world events (~5 minutes at 1s/tick). */
    public static final int DEFAULT_MIN_INTERVAL_TICKS = 300;

    /** Default upper bound (in ticks) of the gap between world events (~10 minutes at 1s/tick). */
    public static final int DEFAULT_MAX_INTERVAL_TICKS = 600;

    /** Default lifetime (in ticks) of an open world event before it despawns unkilled (~2 minutes). */
    public static final int DEFAULT_WINDOW_TICKS = 120;

    private static final GameConfig CONFIG = GameConfig.load();

    private WorldEventSettings() {
    }

    /**
     * The lower bound, in ticks, of the randomized gap between world events.
     *
     * @return the minimum inter-event interval in ticks (at least 1)
     */
    public static int minIntervalTicks() {
        int ticks = CONFIG.getInt("jmud.world_event.min_interval_ticks", DEFAULT_MIN_INTERVAL_TICKS);
        if (ticks < 1) {
            throw new IllegalArgumentException("World-event min interval ticks must be >= 1");
        }
        return ticks;
    }

    /**
     * The upper bound, in ticks, of the randomized gap between world events; must be at least
     * {@link #minIntervalTicks()}.
     *
     * @return the maximum inter-event interval in ticks
     */
    public static int maxIntervalTicks() {
        int ticks = CONFIG.getInt("jmud.world_event.max_interval_ticks", DEFAULT_MAX_INTERVAL_TICKS);
        if (ticks < minIntervalTicks()) {
            throw new IllegalArgumentException("World-event max interval ticks must be >= min interval ticks");
        }
        return ticks;
    }

    /**
     * The lifetime, in ticks, an open world event stays killable before its mob despawns unkilled.
     *
     * @return the world-event window in ticks (at least 1)
     */
    public static int windowTicks() {
        int ticks = CONFIG.getInt("jmud.world_event.window_ticks", DEFAULT_WINDOW_TICKS);
        if (ticks < 1) {
            throw new IllegalArgumentException("World-event window ticks must be >= 1");
        }
        return ticks;
    }
}

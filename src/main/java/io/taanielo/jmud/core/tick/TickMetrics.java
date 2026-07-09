package io.taanielo.jmud.core.tick;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of a single tick's performance, captured on the tick thread by
 * {@link FixedRateTickScheduler} and handed to {@link TickMetricsService} for aggregation.
 *
 * @param tickNumber        monotonically increasing sequence number of this tick since scheduler start
 * @param durationNanos     wall-clock duration of the whole tick in nanoseconds
 * @param tickableCostNanos per-{@link Tickable} cost keyed by the tickable's simple class name,
 *                          in nanoseconds; copied defensively so callers cannot mutate it later
 * @param overran           whether the tick exceeded its configured budget
 */
public record TickMetrics(
    long tickNumber,
    long durationNanos,
    Map<String, Long> tickableCostNanos,
    boolean overran
) {
    /**
     * Canonical constructor. Defensively copies the per-tickable cost map so the recorded
     * snapshot is fully immutable.
     */
    public TickMetrics {
        tickableCostNanos = Map.copyOf(Objects.requireNonNull(tickableCostNanos, "tickableCostNanos is required"));
    }
}

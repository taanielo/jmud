package io.taanielo.jmud.core.tick;

import org.jspecify.annotations.Nullable;

/**
 * Aggregated, read-only view over a window of recent {@link TickMetrics}, produced by
 * {@link TickMetricsService#getSummary()} for display by the wizard {@code STATS} command.
 *
 * <p>Window-scoped fields ({@link #averageDurationNanos}, {@link #maxDurationNanos},
 * {@link #overrunCount} and the slowest-tickable fields) describe only the retained window
 * (the most recent N ticks). {@link #totalTicksRecorded} is a monotonic uptime counter that
 * survives window rotation.
 *
 * @param totalTicksRecorded        total number of ticks recorded since scheduler start (uptime)
 * @param windowTicks               number of ticks in the retained window this summary describes
 * @param averageDurationNanos      mean tick duration over the window, in nanoseconds
 * @param maxDurationNanos          longest single tick duration in the window, in nanoseconds
 * @param slowestTickableName       simple class name of the tickable with the greatest aggregate
 *                                  cost over the window, or {@code null} when no ticks are recorded
 * @param slowestTickableTotalNanos aggregate cost of the slowest tickable over the window, in nanoseconds
 * @param slowestTickableInvocations number of times the slowest tickable ran over the window
 * @param overrunCount              number of ticks in the window that exceeded their budget
 */
public record TickStatsSummary(
    long totalTicksRecorded,
    int windowTicks,
    double averageDurationNanos,
    long maxDurationNanos,
    @Nullable String slowestTickableName,
    long slowestTickableTotalNanos,
    long slowestTickableInvocations,
    long overrunCount
) {

    /**
     * Returns an empty summary representing "no ticks recorded yet".
     *
     * @return a zeroed summary with a {@code null} slowest tickable
     */
    public static TickStatsSummary empty() {
        return new TickStatsSummary(0L, 0, 0.0, 0L, null, 0L, 0L, 0L);
    }
}

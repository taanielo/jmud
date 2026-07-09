package io.taanielo.jmud.core.tick;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain service that aggregates recent {@link TickMetrics} for observability.
 *
 * <p>State is confined to the tick thread (AGENTS.md §5): {@link #recordTick(TickMetrics)} is
 * called by {@link FixedRateTickScheduler} at the end of every tick, and the query methods are
 * invoked by the wizard {@code STATS} command, which executes on the same tick thread via the
 * player command queue. Because reads and writes share one thread, no locking is required.
 *
 * <p>Recent snapshots are held in a fixed-size ring (an {@link ArrayDeque} bounded to the
 * configured retention count) so per-tick recording reuses the backing array and does not grow
 * without bound. A separate monotonic counter tracks total uptime ticks beyond the window.
 */
public class TickMetricsService {

    private static final int DEFAULT_RETENTION = 100;

    private final int retention;
    private final Deque<TickMetrics> recent;
    private long totalTicksRecorded;

    /**
     * Creates a service that retains the default number of recent ticks (100).
     */
    public TickMetricsService() {
        this(DEFAULT_RETENTION);
    }

    /**
     * Creates a service retaining the most recent {@code retention} ticks.
     *
     * @param retention number of recent ticks to keep for window statistics; must be positive
     */
    public TickMetricsService(int retention) {
        if (retention <= 0) {
            throw new IllegalArgumentException("Retention must be positive");
        }
        this.retention = retention;
        this.recent = new ArrayDeque<>(retention);
    }

    /**
     * Records a completed tick, evicting the oldest retained snapshot once the window is full.
     *
     * <p>Must be called on the tick thread only.
     *
     * @param metrics the metrics captured for the tick just completed
     */
    public void recordTick(TickMetrics metrics) {
        if (recent.size() == retention) {
            recent.removeFirst();
        }
        recent.addLast(metrics);
        totalTicksRecorded++;
    }

    /**
     * Returns a read-only, oldest-first snapshot of the retained tick metrics.
     *
     * <p>Must be called on the tick thread only.
     *
     * @return an immutable copy of the current window of tick metrics
     */
    public List<TickMetrics> getRecentMetrics() {
        return List.copyOf(recent);
    }

    /**
     * Aggregates the retained window into a {@link TickStatsSummary} for display.
     *
     * <p>Must be called on the tick thread only.
     *
     * @return the summary over the current window, or {@link TickStatsSummary#empty()} when no
     *         ticks have been recorded yet
     */
    public TickStatsSummary getSummary() {
        if (recent.isEmpty()) {
            return new TickStatsSummary(totalTicksRecorded, 0, 0.0, 0L, null, 0L, 0L, 0L);
        }

        long totalDuration = 0L;
        long maxDuration = 0L;
        long overruns = 0L;
        Map<String, long[]> perTickable = new HashMap<>();

        for (TickMetrics metrics : recent) {
            totalDuration += metrics.durationNanos();
            maxDuration = Math.max(maxDuration, metrics.durationNanos());
            if (metrics.overran()) {
                overruns++;
            }
            for (Map.Entry<String, Long> entry : metrics.tickableCostNanos().entrySet()) {
                long[] agg = perTickable.computeIfAbsent(entry.getKey(), k -> new long[2]);
                agg[0] += entry.getValue();
                agg[1] += 1;
            }
        }

        int windowTicks = recent.size();
        double averageDuration = (double) totalDuration / windowTicks;

        String slowestName = null;
        long slowestTotal = 0L;
        long slowestInvocations = 0L;
        for (Map.Entry<String, long[]> entry : perTickable.entrySet()) {
            long total = entry.getValue()[0];
            if (slowestName == null || total > slowestTotal) {
                slowestName = entry.getKey();
                slowestTotal = total;
                slowestInvocations = entry.getValue()[1];
            }
        }

        return new TickStatsSummary(
            totalTicksRecorded,
            windowTicks,
            averageDuration,
            maxDuration,
            slowestName,
            slowestTotal,
            slowestInvocations,
            overruns
        );
    }
}

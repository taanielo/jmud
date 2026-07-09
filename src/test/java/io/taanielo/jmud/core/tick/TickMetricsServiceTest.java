package io.taanielo.jmud.core.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TickMetricsService}: ring-buffer rotation and summary aggregation.
 */
class TickMetricsServiceTest {

    private static TickMetrics tick(long number, long durationMs, boolean overran, Map<String, Long> costsMs) {
        Map<String, Long> costsNanos = costsMs.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> TimeUnit.MILLISECONDS.toNanos(e.getValue())));
        return new TickMetrics(number, TimeUnit.MILLISECONDS.toNanos(durationMs), costsNanos, overran);
    }

    @Test
    void emptyServiceReturnsEmptySummary() {
        TickMetricsService service = new TickMetricsService(10);

        TickStatsSummary summary = service.getSummary();

        assertEquals(0L, summary.totalTicksRecorded());
        assertEquals(0, summary.windowTicks());
        assertNull(summary.slowestTickableName());
        assertTrue(service.getRecentMetrics().isEmpty());
    }

    @Test
    void rejectsNonPositiveRetention() {
        assertThrows(IllegalArgumentException.class, () -> new TickMetricsService(0));
        assertThrows(IllegalArgumentException.class, () -> new TickMetricsService(-1));
    }

    @Test
    void ringBufferEvictsOldestBeyondRetention() {
        TickMetricsService service = new TickMetricsService(3);

        for (long i = 1; i <= 5; i++) {
            service.recordTick(tick(i, 10, false, Map.of("A", 10L)));
        }

        var recent = service.getRecentMetrics();
        assertEquals(3, recent.size(), "window is capped at the retention count");
        assertEquals(3L, recent.get(0).tickNumber(), "oldest retained is tick #3");
        assertEquals(5L, recent.get(2).tickNumber(), "newest retained is tick #5");
        assertEquals(5L, service.getSummary().totalTicksRecorded(), "uptime counter survives rotation");
    }

    @Test
    void summaryComputesAverageMaxAndOverruns() {
        TickMetricsService service = new TickMetricsService(10);
        service.recordTick(tick(1, 10, false, Map.of("A", 10L)));
        service.recordTick(tick(2, 30, true, Map.of("A", 30L)));
        service.recordTick(tick(3, 20, false, Map.of("A", 20L)));

        TickStatsSummary summary = service.getSummary();

        assertEquals(3, summary.windowTicks());
        assertEquals(3L, summary.totalTicksRecorded());
        assertEquals(1L, summary.overrunCount());
        assertEquals(TimeUnit.MILLISECONDS.toNanos(30), summary.maxDurationNanos());
        assertEquals(TimeUnit.MILLISECONDS.toNanos(20), (long) summary.averageDurationNanos());
    }

    @Test
    void summaryPicksSlowestTickableByAggregateCost() {
        TickMetricsService service = new TickMetricsService(10);
        service.recordTick(tick(1, 100, false, Map.of("Fast", 10L, "Slow", 40L)));
        service.recordTick(tick(2, 100, false, Map.of("Fast", 10L, "Slow", 40L)));

        TickStatsSummary summary = service.getSummary();

        assertEquals("Slow", summary.slowestTickableName());
        assertEquals(TimeUnit.MILLISECONDS.toNanos(80), summary.slowestTickableTotalNanos());
        assertEquals(2L, summary.slowestTickableInvocations());
    }
}

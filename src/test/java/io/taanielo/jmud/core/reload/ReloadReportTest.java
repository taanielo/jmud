package io.taanielo.jmud.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReloadReportTest {

    @Test
    void rendersPluralSummary() {
        assertEquals("Reloaded 42 rooms, 156 items, 8 mobs.", new ReloadReport(42, 156, 8).summary());
    }

    @Test
    void rendersSingularNouns() {
        assertEquals("Reloaded 1 room, 1 item, 1 mob.", new ReloadReport(1, 1, 1).summary());
    }

    @Test
    void rendersZeroCountsAsPlural() {
        assertEquals("Reloaded 0 rooms, 0 items, 0 mobs.", new ReloadReport(0, 0, 0).summary());
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> new ReloadReport(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReloadReport(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReloadReport(0, 0, -1));
    }
}

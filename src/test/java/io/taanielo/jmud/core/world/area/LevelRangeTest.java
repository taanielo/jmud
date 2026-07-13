package io.taanielo.jmud.core.world.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LevelRangeTest {

    @Test
    void acceptsOrderedBounds() {
        LevelRange range = new LevelRange(14, 22);

        assertEquals(14, range.min());
        assertEquals(22, range.max());
    }

    @Test
    void acceptsSinglePointBand() {
        LevelRange range = new LevelRange(5, 5);

        assertEquals(5, range.min());
        assertEquals(5, range.max());
    }

    @Test
    void rejectsMaxBelowMin() {
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> new LevelRange(8, 3));

        assertEquals(true, ex.getMessage().contains("must not be below min"));
    }

    @Test
    void rejectsNegativeMin() {
        assertThrows(IllegalArgumentException.class, () -> new LevelRange(-1, 5));
    }

    @Test
    void describeRendersLabel() {
        assertEquals("levels 20-28", new LevelRange(20, 28).describe());
    }
}

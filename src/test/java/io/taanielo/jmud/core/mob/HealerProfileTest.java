package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link HealerProfile} value-object invariants (issue #733): positive heal amounts, an
 * ordered heal range, and a wounded threshold within {@code [1, 100]}.
 */
class HealerProfileTest {

    @Test
    void acceptsAValidProfile() {
        HealerProfile profile = new HealerProfile(8, 16, 50);
        assertEquals(8, profile.healMin());
        assertEquals(16, profile.healMax());
        assertEquals(50, profile.thresholdPercent());
    }

    @Test
    void rejectsNonPositiveHealMin() {
        assertThrows(IllegalArgumentException.class, () -> new HealerProfile(0, 10, 50));
    }

    @Test
    void rejectsMaxBelowMin() {
        assertThrows(IllegalArgumentException.class, () -> new HealerProfile(12, 8, 50));
    }

    @Test
    void rejectsThresholdOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new HealerProfile(8, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> new HealerProfile(8, 16, 101));
    }
}

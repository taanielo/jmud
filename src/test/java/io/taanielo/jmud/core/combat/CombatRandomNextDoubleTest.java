package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link CombatRandom#nextDouble()} contract across the port implementations:
 * every value lies in {@code [0.0, 1.0)}, the seeded implementation is reproducible under a
 * fixed seed, and the interface default (used by lightweight implementations that only supply
 * {@link CombatRandom#roll(int, int)}) stays within range.
 */
class CombatRandomNextDoubleTest {

    @Test
    void seededNextDouble_isDeterministicAndInRange() {
        SeededCombatRandom a = new SeededCombatRandom(555L);
        SeededCombatRandom b = new SeededCombatRandom(555L);
        for (int i = 0; i < 50; i++) {
            double da = a.nextDouble();
            double db = b.nextDouble();
            assertEquals(da, db, "Same seed must produce identical nextDouble sequences");
            assertTrue(da >= 0.0 && da < 1.0, "nextDouble out of range: " + da);
        }
    }

    @Test
    void defaultCombatRandomNextDouble_isInRange() {
        DefaultCombatRandom random = new DefaultCombatRandom(new Random(7L));
        for (int i = 0; i < 50; i++) {
            double d = random.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0, "nextDouble out of range: " + d);
        }
        assertTrue(random.roll(1, 6) >= 1, "roll should honour its lower bound");
    }

    @Test
    void threadLocalCombatRandomNextDouble_isInRange() {
        ThreadLocalCombatRandom random = new ThreadLocalCombatRandom();
        for (int i = 0; i < 50; i++) {
            double d = random.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0, "nextDouble out of range: " + d);
        }
    }

    @Test
    void interfaceDefaultNextDouble_derivesFromRoll_andStaysInRange() {
        // A minimal implementation that only supplies roll(...) exercises the default nextDouble.
        CombatRandom rollOnly = (min, max) -> max; // deterministic upper-bound roll
        double d = rollOnly.nextDouble();
        assertTrue(d >= 0.0 && d < 1.0, "default nextDouble out of range: " + d);
        assertEquals(999_999 / 1_000_000.0d, d, 1e-12);
    }
}

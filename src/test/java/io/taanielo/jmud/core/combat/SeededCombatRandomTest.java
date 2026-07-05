package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SeededCombatRandomTest {

    @Test
    void sameSeedProducesIdenticalRollSequence() {
        SeededCombatRandom rng1 = new SeededCombatRandom(42L);
        SeededCombatRandom rng2 = new SeededCombatRandom(42L);

        for (int i = 0; i < 20; i++) {
            assertEquals(rng1.roll(1, 100), rng2.roll(1, 100),
                "Roll " + i + " should be identical for the same seed");
        }
    }

    @Test
    void differentSeedProducesDifferentSequence() {
        SeededCombatRandom rng1 = new SeededCombatRandom(1L);
        SeededCombatRandom rng2 = new SeededCombatRandom(2L);

        boolean anyDifference = false;
        for (int i = 0; i < 20; i++) {
            if (rng1.roll(1, 100) != rng2.roll(1, 100)) {
                anyDifference = true;
                break;
            }
        }
        // Two different seeds should diverge in at least one of 20 rolls
        // (probability of all 20 matching by chance is astronomically small)
        org.junit.jupiter.api.Assertions.assertTrue(anyDifference,
            "Different seeds should produce different roll sequences");
    }

    @Test
    void seedAccessorReturnsConstructorSeed() {
        long seed = 0xDEADBEEFCAFEL;
        SeededCombatRandom rng = new SeededCombatRandom(seed);
        assertEquals(seed, rng.seed());
    }

    @Test
    void rollsAreWithinBounds() {
        SeededCombatRandom rng = new SeededCombatRandom(99999L);
        for (int i = 0; i < 1000; i++) {
            int roll = rng.roll(1, 20);
            org.junit.jupiter.api.Assertions.assertTrue(roll >= 1 && roll <= 20,
                "Roll " + roll + " out of [1,20]");
        }
    }

    @Test
    void rollsRangeOfOne() {
        SeededCombatRandom rng = new SeededCombatRandom(7L);
        assertEquals(5, rng.roll(5, 5), "Range of one must always return that value");
    }

    @Test
    void rollThrowsWhenMaxLessThanMin() {
        SeededCombatRandom rng = new SeededCombatRandom(1L);
        assertThrows(IllegalArgumentException.class, () -> rng.roll(10, 5));
    }

    // ── SeededCombatRandomProvider seed derivation ────────────────────────

    @Test
    void sameWorldSeedTickAndActorProduceSameSeed() {
        long s1 = SeededCombatRandomProvider.deriveEncounterSeed(100L, 5L, "hero");
        long s2 = SeededCombatRandomProvider.deriveEncounterSeed(100L, 5L, "hero");
        assertEquals(s1, s2, "Same inputs must always derive the same seed");
    }

    @Test
    void differentTickProducesDifferentSeed() {
        long s1 = SeededCombatRandomProvider.deriveEncounterSeed(100L, 5L, "hero");
        long s2 = SeededCombatRandomProvider.deriveEncounterSeed(100L, 6L, "hero");
        assertNotEquals(s1, s2, "Different ticks must derive different seeds");
    }

    @Test
    void differentActorProducesDifferentSeed() {
        long s1 = SeededCombatRandomProvider.deriveEncounterSeed(100L, 5L, "hero");
        long s2 = SeededCombatRandomProvider.deriveEncounterSeed(100L, 5L, "villain");
        assertNotEquals(s1, s2, "Different actors must derive different seeds");
    }

    @Test
    void differentWorldSeedProducesDifferentSeed() {
        long s1 = SeededCombatRandomProvider.deriveEncounterSeed(1L, 5L, "hero");
        long s2 = SeededCombatRandomProvider.deriveEncounterSeed(2L, 5L, "hero");
        assertNotEquals(s1, s2, "Different world seeds must derive different encounter seeds");
    }

    @Test
    void providerForEncounterReturnsSeededRandom() {
        SeededCombatRandomProvider provider = new SeededCombatRandomProvider(42L);
        CombatRandom r1 = provider.forEncounter(10L, "alice");
        CombatRandom r2 = provider.forEncounter(10L, "alice");

        // Both should be SeededCombatRandom with the same seed
        org.junit.jupiter.api.Assertions.assertInstanceOf(SeededCombatRandom.class, r1);
        org.junit.jupiter.api.Assertions.assertInstanceOf(SeededCombatRandom.class, r2);
        assertEquals(((SeededCombatRandom) r1).seed(), ((SeededCombatRandom) r2).seed());
    }
}

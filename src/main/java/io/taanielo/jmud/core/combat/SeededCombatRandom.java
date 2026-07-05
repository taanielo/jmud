package io.taanielo.jmud.core.combat;

import java.util.SplittableRandom;

/**
 * A {@link CombatRandom} implementation backed by a seeded {@link SplittableRandom},
 * producing deterministic and reproducible combat results for a given seed.
 *
 * <p>The seed is retrievable via {@link #seed()} and is recorded in combat audit
 * entries so that any encounter can be replayed exactly given the same world seed,
 * tick, and actor identifier.
 */
public class SeededCombatRandom implements CombatRandom {
    private final SplittableRandom rng;
    private final long seed;

    /**
     * Creates a seeded combat RNG using the given 64-bit seed.
     *
     * @param seed the seed for the underlying generator
     */
    public SeededCombatRandom(long seed) {
        this.seed = seed;
        this.rng = new SplittableRandom(seed);
    }

    /**
     * Returns the seed this instance was constructed with, enabling exact replay.
     *
     * @return the seed value
     */
    public long seed() {
        return seed;
    }

    /**
     * Rolls an integer in the range {@code [minInclusive, maxInclusive]} using
     * the seeded generator.
     *
     * @param minInclusive lower bound (inclusive)
     * @param maxInclusive upper bound (inclusive)
     * @return a deterministic value in the specified range
     * @throws IllegalArgumentException if {@code maxInclusive < minInclusive}
     */
    @Override
    public int roll(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException("Max roll must be >= min roll");
        }
        int bound = maxInclusive - minInclusive + 1;
        return rng.nextInt(bound) + minInclusive;
    }
}

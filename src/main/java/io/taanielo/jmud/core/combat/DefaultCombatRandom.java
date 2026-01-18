package io.taanielo.jmud.core.combat;

import java.util.Random;

/**
 * Default combat random source backed by {@link Random}.
 */
public class DefaultCombatRandom implements CombatRandom {
    private final Random random;

    public DefaultCombatRandom() {
        this(new Random());
    }

    public DefaultCombatRandom(Random random) {
        this.random = random;
    }

    @Override
    public int roll(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException("Max roll must be >= min roll");
        }
        int bound = maxInclusive - minInclusive + 1;
        return random.nextInt(bound) + minInclusive;
    }
}

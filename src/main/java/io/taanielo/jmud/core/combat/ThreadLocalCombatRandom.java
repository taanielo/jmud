package io.taanielo.jmud.core.combat;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat random source backed by {@link ThreadLocalRandom}.
 */
public class ThreadLocalCombatRandom implements CombatRandom {
    @Override
    public int roll(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException("Max roll must be >= min roll");
        }
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }
}

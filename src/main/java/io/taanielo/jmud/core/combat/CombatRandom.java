package io.taanielo.jmud.core.combat;

/**
 * Random source for deterministic combat rolls.
 */
public interface CombatRandom {
    int roll(int minInclusive, int maxInclusive);
}

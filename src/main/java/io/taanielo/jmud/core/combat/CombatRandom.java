package io.taanielo.jmud.core.combat;

/**
 * Random source for deterministic combat and world rolls.
 *
 * <p>Although named for combat, this is the single RNG port routed through by all
 * game logic that needs randomness (combat damage, mob wandering, gold drops, loot
 * drops, flee direction). Routing every roll through this port keeps behaviour
 * reproducible from a world seed and unit-testable as an exact sequence
 * (AGENTS.md §5 — randomness must never use bare {@code Random}/{@code ThreadLocalRandom}).
 */
public interface CombatRandom {

    /**
     * Rolls an integer in the inclusive range {@code [minInclusive, maxInclusive]}.
     *
     * @param minInclusive lower bound (inclusive)
     * @param maxInclusive upper bound (inclusive)
     * @return a value in the specified range
     */
    int roll(int minInclusive, int maxInclusive);

    /**
     * Rolls a fractional value in the half-open range {@code [0.0, 1.0)}, suitable for
     * probability checks such as a wander chance or a loot drop chance.
     *
     * <p>The default implementation derives the value from {@link #roll(int, int)} so
     * that existing implementations remain valid; deterministic implementations should
     * override this to draw directly from their underlying generator.
     *
     * @return a value in {@code [0.0, 1.0)}
     */
    default double nextDouble() {
        return roll(0, 999_999) / 1_000_000.0d;
    }
}

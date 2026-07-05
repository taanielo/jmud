package io.taanielo.jmud.core.combat;

/**
 * Factory that creates a {@link CombatRandom} scoped to a single combat encounter.
 *
 * <p>Implementations typically derive a per-encounter seed from a combination of
 * the world seed, the current tick, and the attacking actor's identifier, ensuring
 * that combat results are deterministic and replayable given the same inputs.
 *
 * <p>The default production implementation is {@link SeededCombatRandomProvider};
 * tests use lambda wrappers around a fixed {@link CombatRandom}.
 */
@FunctionalInterface
public interface CombatRandomProvider {

    /**
     * Returns a {@link CombatRandom} for the encounter identified by the given
     * tick and actor.
     *
     * @param tick    the current world tick at the moment of resolution
     * @param actorId the unique identifier of the attacking character
     * @return a {@link CombatRandom} scoped to this encounter
     */
    CombatRandom forEncounter(long tick, String actorId);
}

package io.taanielo.jmud.core.combat;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link CombatRandomProvider} that produces deterministic, per-encounter
 * {@link SeededCombatRandom} instances.
 *
 * <p>The per-encounter seed is derived from three inputs:
 * <pre>
 *   seed = mix(worldSeed, tickNumber, actorId.hashCode())
 * </pre>
 * The mixing step uses a Murmur3-style 64-bit finalizer, which provides a good
 * avalanche effect: flipping any input bit changes roughly half the output bits,
 * ensuring that different actors or ticks produce statistically independent
 * roll sequences even when the world seed is held constant.
 *
 * <p>The effective world seed is logged at INFO on construction so that any
 * session can be reconstructed from the logs even when the seed was generated
 * randomly at boot.
 */
@Slf4j
public class SeededCombatRandomProvider implements CombatRandomProvider {

    private final long worldSeed;

    /**
     * Creates a provider using the given world seed.
     *
     * @param worldSeed the base seed shared by all encounters in this session
     */
    public SeededCombatRandomProvider(long worldSeed) {
        this.worldSeed = worldSeed;
        log.info("Combat RNG seeded with worldSeed={} — set jmud.world.seed={} to reproduce this session",
            worldSeed, worldSeed);
    }

    /**
     * Returns the world seed this provider was constructed with.
     *
     * @return the world seed
     */
    public long worldSeed() {
        return worldSeed;
    }

    /**
     * Derives a 64-bit encounter seed from the world seed, the current tick,
     * and the attacker's identifier, then returns a fresh {@link SeededCombatRandom}
     * for that seed.
     *
     * @param tick    the current world tick
     * @param actorId the unique identifier of the attacking character
     * @return a seeded {@link CombatRandom} for this specific encounter
     */
    @Override
    public CombatRandom forEncounter(long tick, String actorId) {
        Objects.requireNonNull(actorId, "actorId is required");
        long seed = deriveEncounterSeed(worldSeed, tick, actorId);
        return new SeededCombatRandom(seed);
    }

    /**
     * Derives the 64-bit encounter seed for the given inputs without constructing
     * a full {@link CombatRandom}. Useful for audit-entry replay tooling that
     * needs to re-compute the seed independently.
     *
     * @param worldSeed the base world seed
     * @param tick      the world tick at the time of the encounter
     * @param actorId   the attacking character's identifier
     * @return the derived encounter seed
     */
    public static long deriveEncounterSeed(long worldSeed, long tick, String actorId) {
        // Mix three inputs using multiply-xor operations, then apply Murmur3-inspired
        // finalizer for avalanche.  Each multiplication uses a different prime so that
        // the three inputs contribute independent bit patterns before mixing.
        long h = worldSeed;
        h ^= Long.rotateLeft(tick * 0x9e3779b97f4a7c15L, 31);
        h ^= (long) actorId.hashCode() * 0x517cc1b727220a95L;
        // Murmur3 finalizer
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }
}

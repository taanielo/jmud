package io.taanielo.jmud.core.world;

import java.util.Objects;

import io.taanielo.jmud.core.combat.CombatRandom;

/**
 * Domain service governing locked containers: whether a rogue's attempt to pick a lock succeeds,
 * whether a hidden trap triggers, how much damage a sprung trap deals, and unlocking the container
 * itself.
 *
 * <p>All rolls go through the injected {@link CombatRandom} port so behaviour stays deterministic
 * under a seeded RNG (AGENTS.md §5), never bare {@code Random}. The rate/damage constants encode
 * the PICK skill balance: a base success chance that scales with rogue level up to a cap, a flat
 * trap chance independent of the pick outcome, and a small damage range for a sprung trap.
 *
 * <p>Operations are pure functions over immutable {@link Item} values — nothing is mutated in
 * place. Callers on the tick thread apply the returned copies.
 */
public class ContainerLockingService {

    /** Base probability that a pick attempt succeeds, before the per-level bonus. */
    static final double BASE_SUCCESS_CHANCE = 0.70;
    /** Additional success probability granted per rogue level. */
    static final double SUCCESS_CHANCE_PER_LEVEL = 0.02;
    /** Maximum success probability, regardless of level. */
    static final double MAX_SUCCESS_CHANCE = 0.95;
    /** Percentage chance (out of 100) that a container's trap triggers on any pick attempt. */
    static final int TRAP_CHANCE_PERCENT = 30;
    /** Minimum HP damage a sprung trap deals. */
    static final int MIN_TRAP_DAMAGE = 5;
    /** Maximum HP damage a sprung trap deals. */
    static final int MAX_TRAP_DAMAGE = 15;

    private final CombatRandom random;

    /**
     * Creates a container-locking service.
     *
     * @param random the random source for pick, trap, and damage rolls; must not be null
     */
    public ContainerLockingService(CombatRandom random) {
        this.random = Objects.requireNonNull(random, "Combat random is required");
    }

    /**
     * Returns a copy of the given container marked as unlocked. The original is left unchanged.
     *
     * @param container the locked container to open; must be a container item
     * @return an unlocked copy of the container
     * @throws IllegalArgumentException if the item is not a container
     */
    public Item unlockContainer(Item container) {
        Objects.requireNonNull(container, "Container is required");
        if (!container.isContainer()) {
            throw new IllegalArgumentException("Only containers can be unlocked");
        }
        return container.withLocked(false);
    }

    /**
     * Calculates the probability, in {@code [0, 1]}, that a rogue of the given level succeeds at a
     * pick attempt: {@link #BASE_SUCCESS_CHANCE} plus {@link #SUCCESS_CHANCE_PER_LEVEL} per level,
     * capped at {@link #MAX_SUCCESS_CHANCE}.
     *
     * @param rogueLevel the rogue's level; must be at least 1
     * @return the success probability
     * @throws IllegalArgumentException if {@code rogueLevel} is less than 1
     */
    public double calculatePickSuccessChance(int rogueLevel) {
        if (rogueLevel < 1) {
            throw new IllegalArgumentException("Rogue level must be at least 1");
        }
        double chance = BASE_SUCCESS_CHANCE + SUCCESS_CHANCE_PER_LEVEL * rogueLevel;
        return Math.min(MAX_SUCCESS_CHANCE, chance);
    }

    /**
     * Rolls whether a pick attempt by a rogue of the given level succeeds, using
     * {@link #calculatePickSuccessChance(int)} as the success probability.
     *
     * @param rogueLevel the rogue's level; must be at least 1
     * @return {@code true} if the attempt succeeds
     */
    public boolean rollPickSuccess(int rogueLevel) {
        int threshold = (int) Math.round(calculatePickSuccessChance(rogueLevel) * 100);
        return random.roll(1, 100) <= threshold;
    }

    /**
     * Rolls whether a container's trap triggers on a pick attempt. The trap chance is fixed at
     * {@link #TRAP_CHANCE_PERCENT}% and is independent of whether the pick itself succeeds.
     *
     * @return {@code true} if the trap triggers
     */
    public boolean shouldTrapTrigger() {
        return random.roll(1, 100) <= TRAP_CHANCE_PERCENT;
    }

    /**
     * Rolls the HP damage dealt by a sprung trap, uniformly in
     * {@code [MIN_TRAP_DAMAGE, MAX_TRAP_DAMAGE]}.
     *
     * @return the trap damage in HP
     */
    public int rollTrapDamage() {
        return random.roll(MIN_TRAP_DAMAGE, MAX_TRAP_DAMAGE);
    }
}

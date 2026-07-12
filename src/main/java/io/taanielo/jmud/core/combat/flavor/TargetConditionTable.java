package io.taanielo.jmud.core.combat.flavor;

import java.util.List;
import java.util.Objects;

/**
 * Resolves a classic-MUD condition phrase from a combatant's current HP relative to its maximum.
 *
 * <p>Tier selection is pure integer math on {@code (hp, maxHp)}: the current HP is expressed as a
 * floored percentage of maximum HP, so only a completely full combatant reads as "in perfect
 * condition". The mapping is deterministic and carries no RNG (AGENTS.md §5). This is the channel a
 * poisoned mob visibly deteriorates through, tick by tick.
 */
public final class TargetConditionTable {
    private final List<TargetConditionTier> tiers;

    /**
     * Creates a condition table from an ordered, non-empty list of tiers.
     *
     * @param tiers the percentage bands; must be non-empty
     */
    public TargetConditionTable(List<TargetConditionTier> tiers) {
        Objects.requireNonNull(tiers, "Condition tiers are required");
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("Condition table must define at least one tier");
        }
        this.tiers = List.copyOf(tiers);
    }

    /**
     * Resolves the condition phrase for a combatant at {@code hp} of {@code maxHp}.
     *
     * @param hp    current HP; values {@code <= 0} resolve to the lowest (worst) tier
     * @param maxHp maximum HP; values {@code <= 0} resolve to the lowest (worst) tier
     * @return the matching condition phrase, e.g. {@code "has quite a few wounds"}
     */
    public String describe(int hp, int maxHp) {
        int percent = percentOfMaxHp(hp, maxHp);
        for (TargetConditionTier tier : tiers) {
            if (tier.matches(percent)) {
                return tier.description();
            }
        }
        // Below the lowest defined band (e.g. a downed combatant at 0%): report the worst tier.
        return worstTier().description();
    }

    /**
     * Returns whether the combatant is completely unhurt (at or above the top tier's floor).
     *
     * @param hp    current HP
     * @param maxHp maximum HP
     * @return {@code true} when the combatant reads as being in perfect condition
     */
    public boolean isPerfect(int hp, int maxHp) {
        return percentOfMaxHp(hp, maxHp) >= topTier().minPercent();
    }

    private int percentOfMaxHp(int hp, int maxHp) {
        if (hp <= 0 || maxHp <= 0) {
            return 0;
        }
        return (int) ((long) hp * 100L / maxHp);
    }

    private TargetConditionTier topTier() {
        TargetConditionTier top = tiers.getFirst();
        for (TargetConditionTier tier : tiers) {
            if (tier.minPercent() > top.minPercent()) {
                top = tier;
            }
        }
        return top;
    }

    private TargetConditionTier worstTier() {
        TargetConditionTier worst = tiers.getFirst();
        for (TargetConditionTier tier : tiers) {
            if (tier.minPercent() < worst.minPercent()) {
                worst = tier;
            }
        }
        return worst;
    }
}

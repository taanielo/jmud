package io.taanielo.jmud.core.combat.flavor;

import java.util.List;
import java.util.Objects;

/**
 * Resolves a classic-MUD damage verb from a hit's magnitude relative to the target.
 *
 * <p>Tier selection is pure integer math on {@code (damage, targetMaxHp)}: the damage is expressed as
 * a floored percentage of the target's maximum HP, so a 6-damage hit is a heavy blow against a 15-HP
 * goblin (40%) but barely a scratch against a 900-HP wyrm (0%). The mapping is deterministic and
 * carries no RNG (AGENTS.md §5).
 */
public final class DamageVerbTable {
    private final List<DamageVerbTier> tiers;

    /**
     * Creates a verb table from an ordered, non-empty list of tiers.
     *
     * @param tiers the percentage bands, lowest first; must be non-empty
     */
    public DamageVerbTable(List<DamageVerbTier> tiers) {
        Objects.requireNonNull(tiers, "Damage verb tiers are required");
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("Damage verb table must define at least one tier");
        }
        this.tiers = List.copyOf(tiers);
    }

    /**
     * Resolves the verb for a hit of {@code damage} against a target with {@code targetMaxHp}.
     *
     * <p>Any landed hit ({@code damage >= 1}) resolves to at least the lowest tier even when it is
     * below the lowest band's percentage (e.g. 1 damage against a very high-HP target rounds down to
     * 0%). Percentages at or above the open-ended top band resolve to the top tier.
     *
     * @param damage      the damage dealt; a value {@code <= 0} yields the lowest tier
     * @param targetMaxHp the target's maximum HP; a value {@code <= 0} yields the top tier
     * @return the matching {@link DamageVerb}
     */
    public DamageVerb verbFor(int damage, int targetMaxHp) {
        int percent = percentOfMaxHp(damage, targetMaxHp);
        for (DamageVerbTier tier : tiers) {
            if (tier.matches(percent)) {
                return tier.verb();
            }
        }
        // Below the lowest defined band (e.g. 0%): fall back to the gentlest verb.
        return tiers.getFirst().verb();
    }

    /**
     * Computes damage as a floored percentage of the target's maximum HP, clamped to {@code [0, 100]}
     * on the low side (an over-100% hit — overkill — is reported as its true percentage so the
     * open-ended top tier can match).
     *
     * @param damage      the damage dealt
     * @param targetMaxHp the target's maximum HP
     * @return the floored percentage; {@code 100} when {@code targetMaxHp <= 0}
     */
    public int percentOfMaxHp(int damage, int targetMaxHp) {
        if (damage <= 0) {
            return 0;
        }
        if (targetMaxHp <= 0) {
            return 100;
        }
        return (int) ((long) damage * 100L / targetMaxHp);
    }
}
